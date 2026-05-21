package fr.alb.billing.service;

import fr.alb.billing.model.RateManagement;
import fr.alb.billing.testutil.ContractFixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RateSelectionServiceTest {

    private final RateSelectionService service = new RateSelectionService();

    @Test
    void selectRate_returnsNull_whenRateListIsEmpty() {
        assertNull(service.selectRate(Collections.emptyList(), LocalDate.of(2026, 6, 1), "EUR", "DAY"));
    }

    @Test
    void selectRate_returnsNull_whenRateListIsNull() {
        assertNull(service.selectRate(null, LocalDate.of(2026, 6, 1), "EUR", "DAY"));
    }

    @Test
    void selectRate_tier1_returnsRateMatchingDateUomAndCurrency() {
        RateManagement r = ContractFixtures.aRate();
        r.setStartDate(java.util.Date.from(java.time.Instant.parse("2026-01-01T00:00:00Z")));
        r.setEndDate(java.util.Date.from(java.time.Instant.parse("2026-12-31T23:59:59Z")));
        r.setUnitOfMeasurement("DAY");
        r.setCurrency("EUR");
        r.setAmount(42.0);

        RateManagement chosen = service.selectRate(List.of(r), LocalDate.of(2026, 6, 1), "EUR", "DAY");
        assertEquals(42.0, chosen.getAmount());
    }

    @Test
    void selectRate_tier1_highestPriorityWins_whenMultipleMatch() {
        RateManagement low = ContractFixtures.aRate();
        low.setStartDate(java.util.Date.from(java.time.Instant.parse("2026-01-01T00:00:00Z")));
        low.setEndDate(java.util.Date.from(java.time.Instant.parse("2026-12-31T23:59:59Z")));
        low.setUnitOfMeasurement("DAY");
        low.setCurrency("EUR");
        low.setPriority(1);
        low.setAmount(50.0);

        RateManagement high = ContractFixtures.aRate();
        high.setStartDate(low.getStartDate());
        high.setEndDate(low.getEndDate());
        high.setUnitOfMeasurement("DAY");
        high.setCurrency("EUR");
        high.setPriority(9);
        high.setAmount(99.0);

        RateManagement chosen = service.selectRate(List.of(low, high), LocalDate.of(2026, 6, 1), "EUR", "DAY");
        assertEquals(99.0, chosen.getAmount());
    }

    @Test
    void selectRate_tier1_defaultFlagBreaksTie_atSamePriority() {
        RateManagement regular = ContractFixtures.aRate();
        regular.setStartDate(java.util.Date.from(java.time.Instant.parse("2026-01-01T00:00:00Z")));
        regular.setEndDate(java.util.Date.from(java.time.Instant.parse("2026-12-31T23:59:59Z")));
        regular.setUnitOfMeasurement("DAY");
        regular.setCurrency("EUR");
        regular.setPriority(5);
        regular.setDefaultRate(false);
        regular.setAmount(50.0);

        RateManagement def = ContractFixtures.aRate();
        def.setStartDate(regular.getStartDate());
        def.setEndDate(regular.getEndDate());
        def.setUnitOfMeasurement("DAY");
        def.setCurrency("EUR");
        def.setPriority(5);
        def.setDefaultRate(true);
        def.setAmount(77.0);

        RateManagement chosen = service.selectRate(List.of(regular, def), LocalDate.of(2026, 6, 1), "EUR", "DAY");
        assertEquals(77.0, chosen.getAmount());
    }

    @Test
    void selectRate_tier2_fallsThroughToDateUomMatchWithoutCurrency() {
        // No rate matches EUR currency, but one matches the date + UoM with USD
        RateManagement usdRate = ContractFixtures.aRate();
        usdRate.setStartDate(java.util.Date.from(java.time.Instant.parse("2026-01-01T00:00:00Z")));
        usdRate.setEndDate(java.util.Date.from(java.time.Instant.parse("2026-12-31T23:59:59Z")));
        usdRate.setUnitOfMeasurement("DAY");
        usdRate.setCurrency("USD");
        usdRate.setAmount(33.0);

        RateManagement chosen = service.selectRate(List.of(usdRate), LocalDate.of(2026, 6, 1), "EUR", "DAY");
        assertEquals(33.0, chosen.getAmount(), "tier 2 should pick the rate even though currency differs");
    }

    @Test
    void selectRate_tier3_fallsThroughToAnyDefaultFlaggedRate() {
        // No date matches, but there is a default-flagged rate
        RateManagement def = ContractFixtures.aRate();
        def.setStartDate(java.util.Date.from(java.time.Instant.parse("2030-01-01T00:00:00Z"))); // way in the future
        def.setEndDate(java.util.Date.from(java.time.Instant.parse("2031-01-01T00:00:00Z")));
        def.setDefaultRate(true);
        def.setAmount(11.0);

        RateManagement chosen = service.selectRate(List.of(def), LocalDate.of(2026, 6, 1), "EUR", "DAY");
        assertEquals(11.0, chosen.getAmount(), "tier 3 should pick a default-flagged rate even when no date matches");
    }

    @Test
    void selectRate_excludesRate_whenDateIsBeforeStartDate() {
        RateManagement r = ContractFixtures.aRate();
        r.setStartDate(java.util.Date.from(java.time.Instant.parse("2026-06-01T00:00:00Z")));
        r.setEndDate(java.util.Date.from(java.time.Instant.parse("2026-12-31T00:00:00Z")));
        r.setUnitOfMeasurement("DAY");
        r.setCurrency("EUR");
        r.setDefaultRate(false);

        RateManagement chosen = service.selectRate(List.of(r), LocalDate.of(2026, 1, 1), "EUR", "DAY");
        assertNull(chosen, "rate should be excluded when query date is before its startDate");
    }

    @Test
    void selectRate_matchesAnyDate_whenRateHasNullStartAndEnd() {
        RateManagement r = ContractFixtures.aRate();
        r.setStartDate(null);
        r.setEndDate(null);
        r.setUnitOfMeasurement("DAY");
        r.setCurrency("EUR");
        r.setAmount(7.0);

        RateManagement chosen = service.selectRate(List.of(r), LocalDate.of(2099, 12, 31), "EUR", "DAY");
        assertEquals(7.0, chosen.getAmount(), "unbounded rate should match any date");
    }

    @Test
    void filterEligibleRates_keepsRateMatchingCategory() {
        RateManagement r = ContractFixtures.aRate();
        r.setApplicableCategory("Import");
        r.setApplicableFreightKind(null);

        List<RateManagement> out = RateSelectionService.filterEligibleRates(List.of(r), "Import", null);
        assertEquals(1, out.size());
    }

    @Test
    void filterEligibleRates_dropsRateWhoseCategoryDiffers() {
        RateManagement r = ContractFixtures.aRate();
        r.setApplicableCategory("Export");

        List<RateManagement> out = RateSelectionService.filterEligibleRates(List.of(r), "Import", null);
        assertEquals(0, out.size(), "rate with non-matching applicableCategory must be filtered out (no fallback)");
    }

    @Test
    void filterEligibleRates_fallsBackToUnconstrainedRates_whenNoSpecificMatch() {
        RateManagement specific = ContractFixtures.aRate();
        specific.setApplicableCategory("Export");

        RateManagement unconstrained = ContractFixtures.aRate();
        unconstrained.setApplicableCategory(null);
        unconstrained.setApplicableFreightKind(null);
        unconstrained.setAmount(99.0);

        List<RateManagement> out = RateSelectionService.filterEligibleRates(
            List.of(specific, unconstrained), "Import", null);
        assertEquals(1, out.size());
        assertEquals(99.0, out.get(0).getAmount(), "fallback returns only unconstrained rates");
    }

    @Test
    void filterEligibleRates_dropsRateWhoseFreightKindDiffers() {
        RateManagement r = ContractFixtures.aRate();
        r.setApplicableCategory(null);
        r.setApplicableFreightKind("FCL");

        List<RateManagement> out = RateSelectionService.filterEligibleRates(List.of(r), null, "LCL");
        assertEquals(0, out.size(), "rate with non-matching applicableFreightKind must be filtered out");
    }
}
