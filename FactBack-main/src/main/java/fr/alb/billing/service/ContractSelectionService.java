package fr.alb.billing.service;

import fr.alb.billing.dao.ContractDao;
import fr.alb.billing.model.Contract;
import fr.alb.billing.model.ContractAddendum;
import fr.alb.billing.model.RateManagement;
import fr.alb.type.CalculationModeType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the single {@link Contract} that applies to an item when billing a
 * given customer, following the Navis N4 model:
 *
 * <ol>
 *   <li><b>Customer scoping</b> — prefer the customer's own contract
 *       (N4 "Contract Customer"). If the customer has no contract, fall back to
 *       global contracts (no {@code customerId}/{@code customerName}); this is
 *       our equivalent of N4's "Payee contract" fallback, since we have no
 *       distinct Payee field.</li>
 *   <li><b>Applicability</b> — the contract must have a {@code calculationMode}
 *       and an eligible rate valid at the invoice date for the item's
 *       category/freightKind. There is no blind {@code rates.get(0)} fallback:
 *       a contract with no rate valid at the date simply does not apply.</li>
 *   <li><b>Addendum precedence</b> — when the contract has an addendum active at
 *       the invoice date, its {@code rateOverrides} take precedence over the
 *       base rates; the most recent active addendum wins (N4 5463-5468).</li>
 *   <li><b>Single selection</b> — among applicable contracts, the highest
 *       {@code priority} wins, ties broken by {@code id} so draft and final
 *       always resolve to the same contract.</li>
 * </ol>
 *
 * <p>The same resolution is used by both the draft-creation path and the
 * finalization pipeline, guaranteeing draft == final.
 */
@ApplicationScoped
public class ContractSelectionService {

    private static final Logger LOGGER = Logger.getLogger(ContractSelectionService.class);

    @ConfigProperty(name = "app.timezone", defaultValue = "Europe/Paris")
    String timezone = "Europe/Paris";

    @Inject
    ContractDao contractDao;

    @Inject
    RateSelectionService rateSelectionService;

    /**
     * The contract that applies to an item, together with its effective rate
     * schedule at the invoice date (base rates, or the active addendum's
     * overrides when one applies).
     */
    public record Selection(Contract contract, List<RateManagement> effectiveRates) {}

    /**
     * Resolve the single contract that applies to {@code item} when billing the
     * given customer on {@code invoiceDate}. Loads active contracts from the DAO
     * and delegates to {@link #resolveFrom}.
     */
    public Optional<Selection> resolve(String customerKey, String customerName,
                                       String itemCategory, String itemFreightKind, String itemId,
                                       LocalDate invoiceDate) {
        return resolveFrom(contractDao.findActiveContracts(), customerKey, customerName,
                itemCategory, itemFreightKind, itemId, invoiceDate);
    }

    /**
     * Pure resolution over a supplied contract list — no DB access, so it can be
     * unit-tested in isolation. Takes the item's category/freightKind as plain
     * strings rather than the Item entity, so the billing context does not reach
     * into the yard context's model.
     */
    Optional<Selection> resolveFrom(List<Contract> active, String customerKey, String customerName,
                                    String itemCategory, String itemFreightKind, String itemId,
                                    LocalDate invoiceDate) {
        if (active == null || active.isEmpty()) {
            return Optional.empty();
        }

        String custKey = normalizeCustomer(customerKey != null ? customerKey : customerName);

        // 1. Customer scoping: the customer's own contracts, else global contracts.
        List<Contract> specific = new ArrayList<>();
        List<Contract> global = new ArrayList<>();
        for (Contract c : active) {
            String cCust = contractCustomerKey(c);
            if (cCust == null) {
                global.add(c);
            } else if (custKey != null && cCust.equals(custKey)) {
                specific.add(c);
            }
        }
        List<Contract> scope = !specific.isEmpty() ? specific : global;
        if (scope.isEmpty()) {
            return Optional.empty();
        }

        // 2. Applicability + effective rates (addendum overlay).
        List<Selection> applicable = new ArrayList<>();
        for (Contract c : scope) {
            if (c.calculationMode == null) {
                continue;
            }
            List<RateManagement> effective = effectiveRates(c, invoiceDate);
            String uom = c.calculationMode.type == CalculationModeType.DATE_BY_TEU ? "DAY" : null;
            RateManagement rate = rateSelectionService.selectRate(
                    effective, invoiceDate, null, uom, itemCategory, itemFreightKind);
            if (rate == null) {
                continue; // no eligible rate valid at the date -> contract does not apply
            }
            applicable.add(new Selection(c, effective));
        }
        if (applicable.isEmpty()) {
            return Optional.empty();
        }

        // 3. Single selection: highest priority, deterministic tie-break by id.
        Optional<Selection> chosen = applicable.stream().max(
                Comparator.comparingInt((Selection s) -> s.contract().priority)
                        .thenComparing(s -> s.contract().getId(), Comparator.nullsLast(Comparator.naturalOrder())));

        if (LOGGER.isDebugEnabled() && chosen.isPresent()) {
            Contract c = chosen.get().contract();
            LOGGER.debugf("[ContractSelect] customer=%s item=%s -> contract=%s (priority=%d, scope=%s)",
                    custKey, itemId, c.getId(), c.priority,
                    specific.isEmpty() ? "global" : "customer");
        }
        return chosen;
    }

    /**
     * Effective rate schedule at the invoice date: the most recent addendum
     * active at that date supplies the rates (its {@code rateOverrides}); if no
     * addendum applies, the contract's base rates are used (N4: addendum > base).
     */
    List<RateManagement> effectiveRates(Contract c, LocalDate invoiceDate) {
        List<RateManagement> base = c.rates != null ? c.rates : List.of();
        if (c.addendums == null || c.addendums.isEmpty() || invoiceDate == null) {
            return base;
        }
        Instant at = invoiceDate.atStartOfDay(ZoneId.of(timezone)).toInstant();
        ContractAddendum mostRecent = c.addendums.stream()
                .filter(a -> a != null && a.isActiveAt(at)
                        && a.getRateOverrides() != null && !a.getRateOverrides().isEmpty())
                .max(Comparator.comparing(ContractAddendum::getValidFrom,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        return mostRecent != null ? mostRecent.getRateOverrides() : base;
    }

    /**
     * Shallow copy of a contract carrying a specific rate schedule. Used to feed
     * the addendum-overlaid effective rates into the calculation pipeline without
     * mutating the shared/cached contract instance (which would corrupt other
     * concurrent invoices — see the {@code @CacheResult} on findActiveContracts).
     */
    public static Contract copyWithRates(Contract src, List<RateManagement> rates) {
        Contract c = new Contract();
        c.setId(src.getId());
        c.name = src.name;
        c.description = src.description;
        c.status = src.status;
        c.startDate = src.startDate;
        c.endDate = src.endDate;
        c.calculationMode = src.calculationMode;
        c.priority = src.priority;
        c.customerId = src.customerId;
        c.customerName = src.customerName;
        c.tariffId = src.tariffId;
        c.rates = rates;
        return c;
    }

    /** Normalized customer key of a contract (customerId, else customerName), or null when the contract is global. */
    private String contractCustomerKey(Contract c) {
        String v = c.customerId != null && !c.customerId.isBlank() ? c.customerId
                : (c.customerName != null && !c.customerName.isBlank() ? c.customerName : null);
        return normalizeCustomer(v);
    }

    private static String normalizeCustomer(String s) {
        return (s == null || s.isBlank()) ? null : s.trim().toUpperCase();
    }
}
