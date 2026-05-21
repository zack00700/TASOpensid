package fr.alb.billing.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import fr.alb.billing.dao.ContractDao;
import fr.alb.billing.dao.TaxCalculationRepository;
import fr.alb.billing.dao.TaxRepository;
import fr.alb.dto.tax.TaxCalculationRequest;
import fr.alb.dto.tax.TaxCalculationResult;
import fr.alb.billing.model.Contract;
import fr.alb.billing.model.RateManagement;
import fr.alb.billing.model.Tax;
import fr.alb.billing.model.TaxCalculation;
import fr.alb.type.TaxType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TaxService {

        private static final Logger LOG = Logger.getLogger(TaxService.class);
        private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
        private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

        @Inject
        TaxRepository taxRepository;

        @Inject
        TaxCalculationRepository taxCalculationRepository;

        @Inject
        ContractDao contractDao;

        public Tax createTax(Tax tax) {
                if (tax == null) {
                        throw new IllegalArgumentException("Tax payload cannot be null");
                }
                validateTaxPayload(tax, true);
                taxRepository.persist(tax);
                return tax;
        }

        public Tax updateTax(String id, Tax partial) {
                if (id == null || id.isBlank()) {
                        throw new IllegalArgumentException("Tax id is required");
                }
                Tax existing = taxRepository.findById(id);
                if (existing == null) {
                        throw new IllegalArgumentException("Tax not found: " + id);
                }
                if (partial == null) {
                        return existing;
                }
                if (partial.getName() != null) {
                        existing.setName(partial.getName());
                }
                if (partial.getCode() != null && !partial.getCode().equals(existing.getCode())) {
                        ensureCodeUnique(partial.getCode(), id);
                        existing.setCode(partial.getCode());
                }
                if (partial.getType() != null) {
                        existing.setType(partial.getType());
                }
                if (partial.getRate() != null) {
                        if (partial.getRate().compareTo(BigDecimal.ZERO) < 0) {
                                throw new IllegalArgumentException("Rate must be positive");
                        }
                        existing.setRate(partial.getRate());
                }
                if (partial.getValidFrom() != null) {
                        existing.setValidFrom(partial.getValidFrom());
                }
                if (partial.getValidTo() != null) {
                        existing.setValidTo(partial.getValidTo());
                }
                if (partial.getValidFrom() != null || partial.getValidTo() != null) {
                        validateDates(existing.getValidFrom(), existing.getValidTo());
                }
                existing.setActive(partial.isActive());
                existing.update();
                return existing;
        }

        public void softDelete(String id) {
                taxRepository.softDelete(id);
        }

        /**
         * Lists active taxes at a given time.
         * Read-only operation - no transaction needed as it only queries data.
         */
        public List<Tax> listActive(Instant at) {
                return taxRepository.findActiveTaxes(at);
        }

        public TaxCalculationResult calculateTaxes(TaxCalculationRequest request) {
                if (request == null) {
                        throw new IllegalArgumentException("Tax calculation request cannot be null");
                }
                if (request.getBaseAmount() == null) {
                        throw new IllegalArgumentException("Base amount is required");
                }
                if (request.getBaseAmount().compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("Base amount must be positive");
                }

                Instant calculationDate = request.getCalculationDate();
                if (calculationDate == null && request.getInvoiceId() != null) {
                        calculationDate = determineInvoiceInstant(request.getInvoiceId());
                }
                if (calculationDate == null) {
                        calculationDate = Instant.now();
                }

                List<ResolvedTax> resolvedTaxes = resolveTaxes(request, calculationDate);
                List<ResolvedTax> inclusive = resolvedTaxes.stream()
                                .filter(ResolvedTax::inclusive)
                                .collect(Collectors.toCollection(ArrayList::new));
                List<ResolvedTax> exclusive = resolvedTaxes.stream()
                                .filter(rt -> !rt.inclusive())
                                .collect(Collectors.toCollection(ArrayList::new));

                BigDecimal grossBase = request.getBaseAmount();
                BigDecimal netBase = grossBase;
                List<RateManagement.TaxBreakdownItem> breakdown = new ArrayList<>();
                BigDecimal inclusiveTotal = BigDecimal.ZERO;

                if (!inclusive.isEmpty()) {
                        InclusiveComputation inclusiveComp = computeInclusive(grossBase, inclusive);
                        netBase = inclusiveComp.netBase();
                        inclusiveTotal = inclusiveComp.totalInclusive();
                        breakdown.addAll(inclusiveComp.items());
                }

                BigDecimal exclusiveTotal = BigDecimal.ZERO;
                for (ResolvedTax tax : exclusive) {
                        BigDecimal baseAmount = netBase;
                        BigDecimal taxAmount;
                        if (tax.tax().getType() == TaxType.PERCENTAGE) {
                                taxAmount = scale(baseAmount.multiply(safeRate(tax.tax()), MC));
                        } else {
                                taxAmount = scale(safeRate(tax.tax()));
                        }
                        if (taxAmount.compareTo(BigDecimal.ZERO) < 0) {
                                taxAmount = BigDecimal.ZERO;
                        }
                        exclusiveTotal = exclusiveTotal.add(taxAmount);
                        breakdown.add(toBreakdownItem(tax.tax(), baseAmount, taxAmount));
                }

                BigDecimal totalTax = inclusiveTotal.add(exclusiveTotal);
                BigDecimal finalAmount;
                if (!inclusive.isEmpty() && request.isInclusive()) {
                        finalAmount = grossBase.add(exclusiveTotal);
                } else {
                        finalAmount = netBase.add(totalTax);
                }

                breakdown.sort(Comparator.comparing(RateManagement.TaxBreakdownItem::getCode,
                                Comparator.nullsLast(String::compareToIgnoreCase)));

                TaxCalculationResult result = new TaxCalculationResult();
                result.setBaseAmount(grossBase);
                result.setNetAmount(netBase);
                result.setInclusiveTaxAmount(inclusiveTotal);
                result.setExclusiveTaxAmount(exclusiveTotal);
                result.setTotalTaxAmount(totalTax);
                result.setFinalAmount(finalAmount);
                result.setCurrency(request.getCurrency());
                result.setTaxBreakdown(breakdown);
                result.setCalculationDate(calculationDate);
                result.setReferences(new TaxCalculationResult.References(
                                request.getContractId(), request.getContractRateId(), request.getInvoiceId()));

                TaxCalculation entity = buildCalculationEntity(request, result, calculationDate);
                taxCalculationRepository.persist(entity);
                result.setCalculationId(entity.getId());

                updateRateSummary(request.getContractId(), request.getContractRateId(), result, calculationDate);

                return result;
        }

        /**
         * Calculates tax inclusive amount.
         * Read-only operation - no transaction needed as it only performs calculations without persisting data.
         */
        public BigDecimal calculateTaxInclusive(BigDecimal gross, List<Tax> inclusiveTaxes) {
                if (gross == null || inclusiveTaxes == null || inclusiveTaxes.isEmpty()) {
                        return gross;
                }
                List<ResolvedTax> resolved = inclusiveTaxes.stream()
                                .map(t -> new ResolvedTax(t, true, null))
                                .collect(Collectors.toList());
                return computeInclusive(gross, resolved).netBase();
        }

        private void validateTaxPayload(Tax tax, boolean checkUniqueCode) {
                if (tax.getName() == null || tax.getName().isBlank()) {
                        throw new IllegalArgumentException("Tax name is required");
                }
                if (tax.getCode() == null || tax.getCode().isBlank()) {
                        throw new IllegalArgumentException("Tax code is required");
                }
                if (checkUniqueCode) {
                        ensureCodeUnique(tax.getCode(), null);
                }
                if (tax.getRate() != null && tax.getRate().compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("Rate must be positive");
                }
                validateDates(tax.getValidFrom(), tax.getValidTo());
        }

        private void ensureCodeUnique(String code, String currentId) {
                Optional<Tax> existing = taxRepository.findByCode(code);
                if (existing.isPresent() && (currentId == null || !Objects.equals(existing.get().getId(), currentId))) {
                        throw new IllegalArgumentException("Tax code already exists: " + code);
                }
        }

        private void validateDates(Instant validFrom, Instant validTo) {
                if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
                        throw new IllegalArgumentException("validTo must be after validFrom");
                }
        }

        private Instant determineInvoiceInstant(String invoiceId) {
                // Fallback: use start of current day. The invoice collection stores LocalDate.
                return LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        }

        private List<ResolvedTax> resolveTaxes(TaxCalculationRequest request, Instant calculationDate) {
                List<ResolvedTax> resolved = new ArrayList<>();
                if (request.getTaxIds() != null && !request.getTaxIds().isEmpty()) {
                        for (String id : request.getTaxIds()) {
                                Tax tax = taxRepository.findById(id);
                                if (tax == null) {
                                        throw new IllegalArgumentException("Tax not found: " + id);
                                }
                                if (!tax.isInForce(calculationDate)) {
                                        throw new IllegalStateException("Tax not active at calculation date: " + tax.getCode());
                                }
                                resolved.add(new ResolvedTax(tax, request.isInclusive(), null));
                        }
                        return resolved;
                }
                if (request.getContractRateId() != null) {
                                RateResolution rateResolution = resolveContractRate(request.getContractRateId(), request.getContractId());
                                if (rateResolution == null) {
                                        throw new IllegalArgumentException("Contract rate not found: " + request.getContractRateId());
                                }
                                List<RateManagement.RateTax> taxes = rateResolution.rate().getTaxes();
                                if (taxes != null) {
                                        for (RateManagement.RateTax rt : taxes) {
                                                if (rt.getTaxId() == null) {
                                                        continue;
                                                }
                                                Tax tax = taxRepository.findById(rt.getTaxId());
                                                if (tax == null) {
                                                        continue;
                                                }
                                                if (!tax.isInForce(calculationDate)) {
                                                        continue;
                                                }
                                                resolved.add(new ResolvedTax(tax, rt.isInclusive(), rt));
                                        }
                                }
                                return resolved;
                }
                return resolved;
        }

        private RateResolution resolveContractRate(String rateId, String contractId) {
                if (rateId == null) {
                        return null;
                }
                if (contractId != null) {
                        Contract contract = contractDao.findContract(contractId);
                        if (contract != null) {
                                RateManagement rate = findRate(contract, rateId);
                                if (rate != null) {
                                        if (LOG.isDebugEnabled()) {
                                                LOG.debugf("Resolved contract rate %s within contract %s", rateId, contract.getId());
                                        }
                                        return new RateResolution(contract, rate);
                                }
                        }
                }
                for (Contract contract : contractDao.getContracts()) {
                        RateManagement rate = findRate(contract, rateId);
                        if (rate != null) {
                                if (LOG.isDebugEnabled()) {
                                        LOG.debugf("Resolved contract rate %s within contract %s", rateId, contract.getId());
                                }
                                return new RateResolution(contract, rate);
                        }
                }
                if (LOG.isDebugEnabled()) {
                        LOG.debugf("No contract rate found for id=%s", rateId);
                }
                return null;
        }

        private RateManagement findRate(Contract contract, String rateId) {
                if (contract == null || contract.rates == null) {
                        return null;
                }
                for (RateManagement rate : contract.rates) {
                        if (rate == null) {
                                continue;
                        }
                        String rid = rate.getRateId();
                        if (rid == null || rid.isBlank()) {
                                rid = contract.getId() + ":" + contract.rates.indexOf(rate);
                                rate.setRateId(rid);
                        }
                        if (rateId.equals(rid)) {
                                return rate;
                        }
                }
                return null;
        }

        private InclusiveComputation computeInclusive(BigDecimal grossBase, List<ResolvedTax> inclusive) {
                if (inclusive.isEmpty()) {
                        return new InclusiveComputation(grossBase, BigDecimal.ZERO, List.of());
                }
                boolean allPercentage = inclusive.stream()
                                .allMatch(t -> t.tax().getType() == TaxType.PERCENTAGE);
                List<RateManagement.TaxBreakdownItem> items = new ArrayList<>();
                BigDecimal net = grossBase;
                BigDecimal totalInclusive = BigDecimal.ZERO;
                if (allPercentage) {
                        BigDecimal totalRate = inclusive.stream()
                                        .map(t -> safeRate(t.tax()))
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal divisor = BigDecimal.ONE.add(totalRate);
                        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                                net = BigDecimal.ZERO;
                        } else {
                                net = scale(grossBase.divide(divisor, MC));
                        }
                        for (ResolvedTax tax : inclusive) {
                                BigDecimal taxAmount = scale(net.multiply(safeRate(tax.tax()), MC));
                                totalInclusive = totalInclusive.add(taxAmount);
                                items.add(toBreakdownItem(tax.tax(), net, taxAmount));
                        }
                } else {
                        List<ResolvedTax> ordered = inclusive.stream()
                                        .sorted(Comparator.comparing(rt -> rt.tax().getCode(), Comparator.nullsLast(String::compareToIgnoreCase)))
                                        .toList();
                        BigDecimal current = grossBase;
                        for (ResolvedTax tax : ordered) {
                                BigDecimal taxAmount;
                                BigDecimal baseBefore;
                                if (tax.tax().getType() == TaxType.PERCENTAGE) {
                                        BigDecimal divisor = BigDecimal.ONE.add(safeRate(tax.tax()));
                                        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                                                baseBefore = BigDecimal.ZERO;
                                        } else {
                                                baseBefore = scale(current.divide(divisor, MC));
                                        }
                                        taxAmount = current.subtract(baseBefore);
                                        if (taxAmount.compareTo(BigDecimal.ZERO) < 0) {
                                                taxAmount = BigDecimal.ZERO;
                                        }
                                        current = baseBefore;
                                } else {
                                        taxAmount = scale(safeRate(tax.tax()));
                                        if (taxAmount.compareTo(current) > 0) {
                                                taxAmount = current.max(BigDecimal.ZERO);
                                        }
                                        baseBefore = current.subtract(taxAmount);
                                        if (baseBefore.compareTo(BigDecimal.ZERO) < 0) {
                                                baseBefore = BigDecimal.ZERO;
                                        }
                                        current = baseBefore;
                                }
                                totalInclusive = totalInclusive.add(taxAmount);
                                items.add(toBreakdownItem(tax.tax(), current, taxAmount));
                        }
                        net = current;
                }
                return new InclusiveComputation(net, totalInclusive, items);
        }

        private RateManagement.TaxBreakdownItem toBreakdownItem(Tax tax, BigDecimal base, BigDecimal taxAmount) {
                return new RateManagement.TaxBreakdownItem(
                                tax.getId(),
                                tax.getCode(),
                                tax.getName(),
                                safeRate(tax),
                                tax.getType(),
                                scale(base),
                                scale(taxAmount));
        }

        private BigDecimal safeRate(Tax tax) {
                return tax.getRate() != null ? tax.getRate() : BigDecimal.ZERO;
        }

        private BigDecimal scale(BigDecimal value) {
                if (value == null) {
                        return BigDecimal.ZERO;
                }
                return value.setScale(6, ROUNDING);
        }

        private TaxCalculation buildCalculationEntity(TaxCalculationRequest request, TaxCalculationResult result, Instant calculationDate) {
                TaxCalculation entity = new TaxCalculation();
                entity.setContractId(request.getContractId());
                entity.setContractRateId(request.getContractRateId());
                entity.setInvoiceId(request.getInvoiceId());
                entity.setCalculationDate(calculationDate);
                entity.setBaseAmount(result.getBaseAmount());
                entity.setInclusive(result.getInclusiveTaxAmount() != null && result.getInclusiveTaxAmount().compareTo(BigDecimal.ZERO) > 0);
                entity.setFinalAmount(result.getFinalAmount());
                List<TaxCalculation.AppliedTax> applied = result.getTaxBreakdown().stream()
                                .map(item -> new TaxCalculation.AppliedTax(
                                                item.getTaxId(),
                                                item.getCode(),
                                                item.getName(),
                                                item.getType(),
                                                item.getRate(),
                                                item.getBaseAmount(),
                                                item.getTaxAmount()))
                                .collect(Collectors.toCollection(ArrayList::new));
                entity.setAppliedTaxes(applied);
                if (request.getTriggeredBy() != null || request.getSource() != null || request.getCorrelationId() != null
                                || request.getMetadata() != null) {
                        entity.setMetadata(new TaxCalculation.CalculationMetadata(
                                        request.getTriggeredBy(),
                                        request.getSource(),
                                        request.getCorrelationId(),
                                        request.getMetadata()));
                }
                return entity;
        }

        private void updateRateSummary(String contractId, String rateId, TaxCalculationResult result, Instant calculationDate) {
                if (rateId == null) {
                        return;
                }
                RateResolution resolution = resolveContractRate(rateId, contractId);
                if (resolution == null) {
                        return;
                }
                RateManagement rate = resolution.rate();
                RateManagement.TaxCalculationSummary summary = new RateManagement.TaxCalculationSummary(
                                result.getBaseAmount(),
                                result.getTotalTaxAmount(),
                                result.getFinalAmount(),
                                result.getTaxBreakdown(),
                                calculationDate,
                                result.getCurrency());
                rate.setLastTaxSummary(summary);
                resolution.contract().update();
        }

        private record ResolvedTax(Tax tax, boolean inclusive, RateManagement.RateTax source) {
                public boolean inclusive() {
                        return inclusive;
                }
        }

        private record RateResolution(Contract contract, RateManagement rate) {}

        private record InclusiveComputation(BigDecimal netBase, BigDecimal totalInclusive,
                        List<RateManagement.TaxBreakdownItem> items) {}
}
