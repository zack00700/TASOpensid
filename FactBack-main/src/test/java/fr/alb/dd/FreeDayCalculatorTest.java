package fr.alb.dd;

import fr.alb.billing.model.RateManagement;
import fr.alb.dd.model.DdDayEntry;
import fr.alb.dd.model.DdRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the free-day / charge computation, focused on the C7 defect: days
 * inside the free-time window must never be charged, and weekends/holidays that
 * the rule excludes must not <em>consume</em> a free day (they extend the free
 * window) — while still being charged once the free allowance is exhausted
 * (the {@code includeWeekends}/{@code includeHolidays} flags govern free-day
 * CONSUMPTION only, per their field documentation).
 *
 * Calendar anchor: June 2026 starts on a Monday — 2026-06-05 = Friday,
 * 2026-06-06 = Saturday, 2026-06-07 = Sunday, 2026-06-08 = Monday.
 */
class FreeDayCalculatorTest {

    private final FreeDayCalculator calc = new FreeDayCalculator();
    private final ZoneId zone = ZoneId.of("Europe/Paris");

    private Instant startOf(int y, int m, int d) {
        return LocalDate.of(y, m, d).atStartOfDay(zone).toInstant();
    }

    private RateManagement openTier(double amount) {
        RateManagement t = new RateManagement();
        t.setStartQuantity(1);
        t.setEndQuantity(0); // open-ended
        t.setAmount(amount);
        t.setCurrency("EUR");
        return t;
    }

    private DdRule rule(int freeDays, boolean includeWeekends, boolean includeHolidays) {
        DdRule r = new DdRule();
        r.freeDays = freeDays;
        r.includeWeekends = includeWeekends;
        r.includeHolidays = includeHolidays;
        r.tiers = List.of(openTier(30));
        return r;
    }

    @Test
    void weekendInsideFreeWindow_isNotCharged() {
        // clockStart on a Saturday, 5 free days, weekends excluded.
        DdRule r = rule(5, false, false);
        List<DdDayEntry> log = calc.buildDailyLog(
                r, startOf(2026, 6, 6), startOf(2026, 6, 6).plusSeconds(3600), List.of(), zone);

        assertEquals(1, log.size());
        DdDayEntry sat = log.get(0);
        assertTrue(sat.isFreeDay, "a weekend inside the free window must be free");
        assertEquals(0, BigDecimal.ZERO.compareTo(sat.chargeAmount),
                "a weekend inside the free window must not be charged");
    }

    @Test
    void excludedWeekend_doesNotConsumeAFreeDay() {
        // Fri start, 2 free days, weekends excluded:
        // Fri(consume 1, free) Sat(free, no consume) Sun(free, no consume) Mon(consume 2, free) Tue(charged).
        DdRule r = rule(2, false, false);
        List<DdDayEntry> log = calc.buildDailyLog(
                r, startOf(2026, 6, 5), startOf(2026, 6, 9).plusSeconds(3600), List.of(), zone);

        assertEquals(5, log.size());
        assertTrue(log.get(0).isFreeDay, "Fri free");
        assertTrue(log.get(1).isFreeDay, "Sat free (does not consume)");
        assertTrue(log.get(2).isFreeDay, "Sun free (does not consume)");
        assertTrue(log.get(3).isFreeDay, "Mon free (2nd free day consumed)");
        assertFalse(log.get(4).isFreeDay, "Tue charged (free days exhausted)");
        assertEquals(0, new BigDecimal("30").compareTo(log.get(4).chargeAmount));
    }

    @Test
    void includeWeekends_countsWeekendsAsConsuming() {
        // Baseline: weekends included -> plain calendar counting.
        // Fri start, 2 free days: Fri(1) Sat(2) free, Sun charged.
        DdRule r = rule(2, true, true);
        List<DdDayEntry> log = calc.buildDailyLog(
                r, startOf(2026, 6, 5), startOf(2026, 6, 7).plusSeconds(3600), List.of(), zone);

        assertEquals(3, log.size());
        assertTrue(log.get(0).isFreeDay);
        assertTrue(log.get(1).isFreeDay);
        assertFalse(log.get(2).isFreeDay, "Sun charged when weekends count toward free days");
    }

    @Test
    void holidayInsideFreeWindow_isFreeAndDoesNotConsume() {
        // Mon start, 2 free days, holidays excluded, Tue is a holiday:
        // Mon(consume 1, free) Tue(holiday, free, no consume) Wed(consume 2, free) Thu(charged).
        // The holiday extends the free window by one day instead of consuming it.
        DdRule r = rule(2, true, false);
        List<DdDayEntry> log = calc.buildDailyLog(
                r, startOf(2026, 6, 8), startOf(2026, 6, 11).plusSeconds(3600),
                List.of("2026-06-09"), zone);

        assertEquals(4, log.size());
        assertTrue(log.get(0).isFreeDay, "Mon free");
        assertTrue(log.get(1).isFreeDay, "holiday Tue free and does not consume");
        assertTrue(log.get(2).isFreeDay, "Wed free (2nd free day consumed)");
        assertFalse(log.get(3).isFreeDay, "Thu charged (free days exhausted)");
    }

    @Test
    void excludedWeekend_isChargedOnceFreeWindowExhausted() {
        // Documents the policy: the exclusion flags affect free-day CONSUMPTION
        // only. Once the allowance is spent, every calendar day is charged,
        // weekends included. Fri start, 1 free day, weekends excluded:
        // Fri(consume 1, free) Sat(charged) Sun(charged).
        DdRule r = rule(1, false, false);
        List<DdDayEntry> log = calc.buildDailyLog(
                r, startOf(2026, 6, 5), startOf(2026, 6, 7).plusSeconds(3600), List.of(), zone);

        assertEquals(3, log.size());
        assertTrue(log.get(0).isFreeDay, "Fri free (consumed the single free day)");
        assertFalse(log.get(1).isFreeDay, "Sat charged: free allowance exhausted");
        assertFalse(log.get(2).isFreeDay, "Sun charged: free allowance exhausted");
    }
}
