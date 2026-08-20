package fr.alb.billing.service;

import fr.alb.billing.model.Contract;
import fr.alb.billing.model.ContractAddendum;
import fr.alb.billing.model.RateManagement;
import fr.alb.billing.testutil.ContractFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ContractSelectionService} — the N4-aligned contract
 * resolver (customer scoping → applicability → addendum precedence → single pick).
 * Uses the real {@link RateSelectionService} so rate eligibility is exercised
 * end-to-end; no DB access (drives the pure {@code resolveFrom} method).
 */
class ContractSelectionServiceTest {

    private ContractSelectionService service;

    private static final LocalDate INVOICE_DATE = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void setUp() throws Exception {
        service = new ContractSelectionService();
        inject(service, "timezone", "Europe/Paris");
        RateSelectionService rateSelection = new RateSelectionService();
        inject(rateSelection, "timezone", "Europe/Paris");
        inject(service, "rateSelectionService", rateSelection);
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Contract globalContract(String id) {
        Contract c = ContractFixtures.aContract();
        c.setId(id);
        c.customerId = null;
        c.customerName = null;
        return c;
    }

    private static Contract customerContract(String id, String customerId) {
        Contract c = ContractFixtures.aContract();
        c.setId(id);
        c.customerId = customerId;
        return c;
    }

    @Test
    void returnsEmpty_whenNoContracts() {
        assertTrue(service.resolveFrom(List.of(), "ACME", "ACME", "Import", null, "item-1", INVOICE_DATE).isEmpty());
    }

    @Test
    void prefersCustomerSpecificContract_overGlobal() {
        Contract global = globalContract("global-1");
        Contract acme = customerContract("acme-1", "ACME");

        Optional<ContractSelectionService.Selection> sel =
                service.resolveFrom(List.of(global, acme), "acme", "ACME Corp", "Import", null, "item-1", INVOICE_DATE);

        assertTrue(sel.isPresent());
        assertSame(acme, sel.get().contract(), "customer-specific contract must win over global");
    }

    @Test
    void fallsBackToGlobal_whenCustomerHasNoContract() {
        Contract global = globalContract("global-1");
        Contract other = customerContract("other-1", "OTHERCO");

        Optional<ContractSelectionService.Selection> sel =
                service.resolveFrom(List.of(global, other), "ACME", "ACME", "Import", null, "item-1", INVOICE_DATE);

        assertTrue(sel.isPresent());
        assertSame(global, sel.get().contract(), "must fall back to the global contract");
    }

    @Test
    void notApplicable_whenNoRateValidAtDate() {
        // Rate window entirely in the past and not a default rate -> not applicable (Q3=a, no blind fallback).
        Contract c = globalContract("global-1");
        RateManagement r = c.rates.get(0);
        r.setDefaultRate(false);
        r.setStartDate(Date.from(Instant.parse("2025-01-01T00:00:00Z")));
        r.setEndDate(Date.from(Instant.parse("2025-12-31T00:00:00Z")));

        assertTrue(service.resolveFrom(List.of(c), "ACME", "ACME", "Import", null, "item-1", INVOICE_DATE).isEmpty());
    }

    @Test
    void higherPriorityWins_withinSameScope() {
        Contract low = globalContract("low");
        low.priority = 1;
        Contract high = globalContract("high");
        high.priority = 5;

        Optional<ContractSelectionService.Selection> sel =
                service.resolveFrom(List.of(low, high), "ACME", "ACME", "Import", null, "item-1", INVOICE_DATE);

        assertTrue(sel.isPresent());
        assertSame(high, sel.get().contract());
    }

    @Test
    void deterministicTieBreak_byId() {
        Contract a = globalContract("aaa");
        Contract b = globalContract("bbb");
        a.priority = 0;
        b.priority = 0;

        // Same priority -> deterministic id tie-break, regardless of input order.
        Contract first = service.resolveFrom(List.of(a, b), "ACME", "ACME", "Import", null, "item-1", INVOICE_DATE).get().contract();
        Contract second = service.resolveFrom(List.of(b, a), "ACME", "ACME", "Import", null, "item-1", INVOICE_DATE).get().contract();

        assertSame(first, second, "same inputs must resolve to the same contract (draft == final)");
    }

    @Test
    void addendumOverridesBaseRate_whenActiveAtDate() {
        Contract c = globalContract("global-1");
        c.rates.get(0).setAmount(50.0); // base rate

        RateManagement override = ContractFixtures.aRate();
        override.setAmount(30.0); // negotiated addendum rate
        ContractAddendum addendum = new ContractAddendum();
        addendum.setValidFrom(Instant.parse("2026-06-01T00:00:00Z"));
        addendum.setValidTo(Instant.parse("2026-09-01T00:00:00Z"));
        addendum.setRateOverrides(new ArrayList<>(List.of(override)));
        c.addendums = new ArrayList<>(List.of(addendum));

        List<RateManagement> effective = service.effectiveRates(c, INVOICE_DATE);
        assertEquals(1, effective.size());
        assertEquals(30.0, effective.get(0).getAmount(), 0.0001, "active addendum overrides base rate");

        Optional<ContractSelectionService.Selection> sel =
                service.resolveFrom(List.of(c), "ACME", "ACME", "Import", null, "item-1", INVOICE_DATE);
        assertTrue(sel.isPresent());
        assertEquals(30.0, sel.get().effectiveRates().get(0).getAmount(), 0.0001);
    }

    @Test
    void usesBaseRate_whenAddendumInactiveAtDate() {
        Contract c = globalContract("global-1");
        c.rates.get(0).setAmount(50.0);

        RateManagement override = ContractFixtures.aRate();
        override.setAmount(30.0);
        ContractAddendum addendum = new ContractAddendum();
        // Addendum window does NOT cover INVOICE_DATE (2026-06-15).
        addendum.setValidFrom(Instant.parse("2026-01-01T00:00:00Z"));
        addendum.setValidTo(Instant.parse("2026-02-01T00:00:00Z"));
        addendum.setRateOverrides(new ArrayList<>(List.of(override)));
        c.addendums = new ArrayList<>(List.of(addendum));

        List<RateManagement> effective = service.effectiveRates(c, INVOICE_DATE);
        assertEquals(50.0, effective.get(0).getAmount(), 0.0001, "inactive addendum -> base rate");
    }
}
