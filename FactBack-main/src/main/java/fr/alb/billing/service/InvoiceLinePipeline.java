package fr.alb.billing.service;

import fr.alb.billing.dao.ContractDao;
import fr.alb.billing.domain.BillingContext;
import fr.alb.billing.domain.ChargeResult;
import fr.alb.dto.CurrencyTotalDto;
import fr.alb.dto.InvoiceLineDto;
import fr.alb.dto.tax.TaxCalculationRequest;
import fr.alb.dto.tax.TaxCalculationResult;
import fr.alb.bol.model.BillOfLading;
import fr.alb.billing.model.Contract;
import fr.alb.model.Event;
import fr.alb.billing.model.Invoice;
import fr.alb.billing.model.InvoiceLineSnap;
import fr.alb.yard.model.Item;
import fr.alb.billing.model.RateManagement;
import fr.alb.type.CalculationModeType;
import fr.alb.type.CalculationSubType;
import fr.alb.billing.service.TaxService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Pipeline responsible for building invoice lines both for draft preview and
 * for final persistence.  The logic here is shared by the HTML preview endpoint
 * and the finalization process so that the amounts remain consistent.
 */
@ApplicationScoped
public class InvoiceLinePipeline {

    private static final Logger LOG = Logger.getLogger(InvoiceLinePipeline.class);

    @ConfigProperty(name = "app.timezone", defaultValue = "Europe/Paris")
    String timezone = "Europe/Paris";

    public record DraftResult(List<InvoiceLineDto> lines, List<String> missingItemIds,
                              int itemCount, String scopeTag) {}

    public record FinalResult(List<InvoiceLineSnap> lines, List<String> missingItemIds,
                              int itemCount, String scopeTag,
                              BigDecimal subtotal,
                              BigDecimal inclusiveTaxTotal,
                              BigDecimal exclusiveTaxTotal,
                              BigDecimal totalTax,
                              BigDecimal grandTotal,
                              List<RateManagement.TaxBreakdownItem> taxBreakdown,
                              List<String> taxCalculationIds) {}

    @Inject
    ContractDao contractDao;

    @Inject
    InvoiceCalculationService invoiceCalculationService;

    @Inject
    RateSelectionService rateSelectionService;

    @Inject
    TaxService taxService;

    /**
     * Build invoice line DTOs for an invoice in draft mode.
     */
    // Enhanced buildForDraft method in InvoiceLinePipeline
    public DraftResult buildForDraft(Invoice inv) {
        ZoneId z = ZoneId.of(timezone);
        LocalDate invoiceDate = inv.createdDate != null ? inv.createdDate : LocalDate.now(z);
        Scope scope = resolveScope(inv);
        List<Item> items = scope.items;

        if (items.isEmpty()) {
            LOG.debugf("[InvoicePipeline] No items linked to invoice=%s", inv.id);
            return new DraftResult(List.of(), List.of(), 0, scope.tag);
        }

        List<Contract> contracts = contractDao.findActiveContracts();
        if (contracts.isEmpty()) {
            LOG.debugf("[InvoicePipeline] No active contracts for invoice=%s", inv.id);
            return new DraftResult(List.of(), List.of(), items.size(), scope.tag);
        }

        List<InvoiceLineDto> lines = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (Item item : items) {
            LOG.debugf("[InvoicePipeline] Processing item %s", item.getId());

            // Resolve all events once (single DB round-trip for all contracts of this item)
            List<Event> allItemEvents = invoiceCalculationService.getItemEvents(item);
            Event inEvent  = allItemEvents.stream().filter(e -> "IN".equals(e.getType())).findFirst().orElse(null);
            Event outEvent = allItemEvents.stream().filter(e -> "OUT".equals(e.getType())).findFirst().orElse(null);

            if (inEvent == null) {
                LOG.debugf("[InvoicePipeline] Skip item %s: no IN event", item.getId());
                missing.add(item.getId());
                continue;
            }

            // Load BillOfLading once if needed (QUANTITY / TIERED / BANDED BL subtypes)
            BillOfLading bol = null;
            if (item.getBillOfLadingId() != null) {
                bol = BillOfLading.findById(item.getBillOfLadingId());
            }

            LocalDate inDate = inEvent.getTimeStamp().toInstant().atZone(z).toLocalDate();
            long days = ChronoUnit.DAYS.between(inDate, invoiceDate);

            for (Contract c : contracts) {
                if (c.calculationMode == null) continue;

                CalculationModeType modeType = c.calculationMode.type;
                CalculationSubType  subType  = CalculationSubType.from(c.calculationMode.subType);

                if (modeType == CalculationModeType.DATE && subType == CalculationSubType.IN_DATE) {
                    // ── DATE / IN_DATE: direct line building ──────────────────────────────
                    if (days <= 0) {
                        LOG.debugf("[InvoicePipeline] Skip item %s: non-positive days=%d", item.getId(), days);
                        continue;
                    }

                    RateManagement rate = rateSelectionService.selectRate(c.rates, invoiceDate, null, "DAY",
                        item.getCategory() != null ? item.getCategory().getValue() : null,
                        item.getFreightKind() != null ? item.getFreightKind().getValue() : null);
                    if (rate == null) {
                        LOG.debugf("[InvoicePipeline] Skip item %s: no rate for contract %s", item.getId(), c.getId());
                        continue;
                    }

                    String rateId = rate.ensureRateId();
                    List<RateManagement.RateTax> taxes = rate.getTaxes() != null ? new ArrayList<>(rate.getTaxes()) : List.of();

                    BigDecimal qty  = BigDecimal.valueOf(days);
                    BigDecimal unit = BigDecimal.valueOf(rate.getAmount());
                    BigDecimal amt  = unit.multiply(qty);
                    String uom      = rate.getUnitOfMeasurement() != null ? rate.getUnitOfMeasurement() : "DAY";
                    String currency = rate.getCurrency() != null ? rate.getCurrency() : "EUR";

                    String desc = String.format("Storage - Gate IN %s (%s) - Contract: %s",
                            DateTimeFormatter.ofPattern("dd/MM/yyyy").format(inDate),
                            item.getItemNumber() != null ? item.getItemNumber() : item.getId(),
                            c.name != null ? c.name : c.getId());

                    lines.add(new InvoiceLineDto(item.getId(), item.getItemNumber(), desc,
                            qty, uom, unit, amt, currency, c.getId(), rateId, taxes));

                    LOG.debugf("[InvoicePipeline] DATE item=%s in=%s days=%d rate=%.2f -> %.2f %s",
                            item.getId(), inDate, days, unit.doubleValue(), amt.doubleValue(), currency);

                } else {
                    // ── Other modes (DATE_BY_TEU, QUANTITY, TIERED, BANDED, SPECIAL) ────
                    // Delegate to the calculator framework with the correct invoice date
                    ChargeResult result = invoiceCalculationService.calculateCharge(
                            c, item, inEvent, outEvent, invoiceDate, bol);

                    if (result == null || result.amount().compareTo(BigDecimal.ZERO) <= 0) {
                        LOG.debugf("[InvoicePipeline] Skip item %s: zero/null result contract=%s mode=%s/%s",
                                item.getId(), c.getId(), modeType, subType);
                        continue;
                    }

                    // Recover the selected rate for rateId and taxes
                    RateManagement rate = c.rates.stream()
                            .filter(r -> result.rateId() != null && result.rateId().equals(r.getRateId()))
                            .findFirst()
                            .orElse(c.rates.isEmpty() ? null : c.rates.get(0));

                    String rateId = rate != null ? rate.ensureRateId() : "";
                    List<RateManagement.RateTax> taxes = rate != null && rate.getTaxes() != null
                            ? new ArrayList<>(rate.getTaxes()) : List.of();
                    BigDecimal unitPrice = result.quantity() != null
                            && result.quantity().compareTo(BigDecimal.ZERO) > 0
                            ? result.amount().divide(result.quantity(), 6, java.math.RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    String currency = result.currency() != null ? result.currency() : "EUR";

                    lines.add(new InvoiceLineDto(item.getId(), item.getItemNumber(),
                            result.explanation(), result.quantity(), result.uom(),
                            unitPrice, result.amount(), currency,
                            c.getId(), rateId, taxes));

                    LOG.debugf("[InvoicePipeline] %s/%s item=%s amt=%.2f %s",
                            modeType, subType, item.getId(), result.amount().doubleValue(), currency);
                }
            }
        }

        BigDecimal total = lines.stream()
                .map(InvoiceLineDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LOG.debugf("[InvoicePipeline] scope=%s items=%d lines=%d missing=%d total=%.2f",
                scope.tag, items.size(), lines.size(), missing.size(), total.doubleValue());

        return new DraftResult(lines, missing, items.size(), scope.tag);
    }

    /**
     * Build persistent invoice line snapshots for finalization.
     */
    public FinalResult buildSnapshotForFinal(Invoice inv) {
        DraftResult draft = buildForDraft(inv);
        List<InvoiceLineSnap> snaps = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal inclusiveTotal = BigDecimal.ZERO;
        BigDecimal exclusiveTotal = BigDecimal.ZERO;
        List<String> taxCalculationIds = new ArrayList<>();
        Map<String, RateManagement.TaxBreakdownItem> aggregated = new LinkedHashMap<>();
        ZoneId zone = ZoneId.of(timezone);
        java.time.Instant calculationInstant = inv.createdDate != null
                ? inv.createdDate.atStartOfDay(zone).toInstant()
                : java.time.Instant.now();

        for (InvoiceLineDto d : draft.lines()) {
            InvoiceLineSnap s = new InvoiceLineSnap();
            s.itemId = d.itemId();
            s.itemNumber = d.itemNumber();
            s.description = d.description();
            s.quantity = d.quantity();
            s.uom = d.uom();
            s.unitPrice = d.unitPrice();
            s.amount = d.amount();
            s.currency = d.currency();
            s.contractId = d.contractId();
            s.contractRateId = d.contractRateId();

            subtotal = subtotal.add(d.amount());

            boolean hasTaxConfig = (d.contractRateId() != null && !d.contractRateId().isBlank())
                    || (d.taxes() != null && !d.taxes().isEmpty());

            if (hasTaxConfig) {
                TaxCalculationRequest request = new TaxCalculationRequest();
                request.setBaseAmount(d.amount());
                request.setContractId(d.contractId());
                request.setContractRateId(d.contractRateId());
                request.setInvoiceId(inv.id);
                request.setCurrency(d.currency());
                request.setInclusive(d.taxes() != null && d.taxes().stream().anyMatch(RateManagement.RateTax::isInclusive));
                request.setCalculationDate(calculationInstant);
                request.setTriggeredBy("SYSTEM");
                request.setSource("INVOICE_FINALIZE");
                request.setCorrelationId(inv.id);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("invoiceId", inv.id);
                if (d.itemId() != null) {
                    metadata.put("itemId", d.itemId());
                }
                request.setMetadata(metadata);

                TaxCalculationResult calcResult = taxService.calculateTaxes(request);
                s.taxBreakdown = calcResult.getTaxBreakdown();
                s.taxTotal = calcResult.getTotalTaxAmount();
                s.finalAmount = calcResult.getFinalAmount();
                s.taxCalculationId = calcResult.getCalculationId();
                s.inclusiveTaxTotal = calcResult.getInclusiveTaxAmount();
                s.exclusiveTaxTotal = calcResult.getExclusiveTaxAmount();
                taxCalculationIds.add(calcResult.getCalculationId());

                Map<String, Boolean> taxLookup = d.taxes() == null ? Map.of()
                        : d.taxes().stream()
                        .filter(rt -> rt.getTaxId() != null)
                        .collect(Collectors.toMap(RateManagement.RateTax::getTaxId, RateManagement.RateTax::isInclusive, (a, b) -> a));

                BigDecimal lineInclusive = BigDecimal.ZERO;
                BigDecimal lineExclusive = BigDecimal.ZERO;
                for (RateManagement.TaxBreakdownItem item : calcResult.getTaxBreakdown()) {
                    boolean inclusiveFlag = taxLookup.getOrDefault(item.getTaxId(), false);
                    if (inclusiveFlag) {
                        lineInclusive = lineInclusive.add(item.getTaxAmount());
                    } else {
                        lineExclusive = lineExclusive.add(item.getTaxAmount());
                    }
                    aggregated.merge(item.getTaxId(), cloneBreakdown(item), (existing, extra) -> {
                        existing.setBaseAmount(existing.getBaseAmount().add(extra.getBaseAmount()));
                        existing.setTaxAmount(existing.getTaxAmount().add(extra.getTaxAmount()));
                        return existing;
                    });
                }
                if (lineInclusive.compareTo(BigDecimal.ZERO) == 0 && calcResult.getInclusiveTaxAmount() != null) {
                    lineInclusive = calcResult.getInclusiveTaxAmount();
                }
                if (lineExclusive.compareTo(BigDecimal.ZERO) == 0 && calcResult.getExclusiveTaxAmount() != null) {
                    lineExclusive = calcResult.getExclusiveTaxAmount();
                }
                inclusiveTotal = inclusiveTotal.add(lineInclusive);
                exclusiveTotal = exclusiveTotal.add(lineExclusive);
            } else {
                s.finalAmount = d.amount();
            }

            snaps.add(s);
        }

        BigDecimal totalTax = inclusiveTotal.add(exclusiveTotal);
        BigDecimal grandTotal = subtotal.add(exclusiveTotal);

        return new FinalResult(snaps, draft.missingItemIds(), draft.itemCount(), draft.scopeTag(),
                subtotal, inclusiveTotal, exclusiveTotal, totalTax, grandTotal,
                new ArrayList<>(aggregated.values()), taxCalculationIds);
    }

    public List<CurrencyTotalDto> totalsByCurrency(List<InvoiceLineDto> lines) {
        Map<String, BigDecimal> map = lines.stream()
                .collect(Collectors.groupingBy(
                        InvoiceLineDto::currency,
                        Collectors.reducing(BigDecimal.ZERO, InvoiceLineDto::amount, BigDecimal::add)
                ));
        return map.entrySet().stream()
                .map(e -> new CurrencyTotalDto(e.getKey(), e.getValue()))
                .sorted(java.util.Comparator.comparing(CurrencyTotalDto::currency))
                .toList();
    }

    private RateManagement.TaxBreakdownItem cloneBreakdown(RateManagement.TaxBreakdownItem item) {
        return new RateManagement.TaxBreakdownItem(
                item.getTaxId(),
                item.getCode(),
                item.getName(),
                item.getRate(),
                item.getType(),
                item.getBaseAmount(),
                item.getTaxAmount());
    }

    // ---------- helpers ---------

    private Scope resolveScope(Invoice inv) {
        @SuppressWarnings("unchecked")
        List<Item> items = Item.list("relatedInvoice", inv.id);
        String tag = "relatedInvoice";

        if (items.isEmpty() && inv.itemIds != null && !inv.itemIds.isEmpty()) {
            items = Item.list("_id in ?1", inv.itemIds);
            tag = "itemIds";
        }

        if (items.isEmpty() && inv.billOfLadingId != null) {
            items = Item.list("billOfLadingId", inv.billOfLadingId);
            tag = "billOfLading";
        }

        return new Scope(items, tag);
    }

    private static class Scope {
        final List<Item> items;
        final String tag;
        Scope(List<Item> items, String tag) {
            this.items = items == null ? Collections.emptyList() : items;
            this.tag = tag;
        }
    }
}

