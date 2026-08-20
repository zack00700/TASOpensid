package fr.alb.dd;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the holiday year-range logic (M11): an accrual must load
 * holidays for every year it spans, not only the clockStart year.
 */
class DdAccrualServiceTest {

    private final ZoneId zone = ZoneId.of("Europe/Paris");

    private Instant day(int y, int m, int d) {
        return LocalDate.of(y, m, d).atStartOfDay(zone).toInstant();
    }

    @Test
    void yearsSpanned_singleYear() {
        assertEquals(List.of(2026), DdAccrualService.yearsSpanned(day(2026, 6, 1), day(2026, 8, 1), zone));
    }

    @Test
    void yearsSpanned_crossesYearBoundary() {
        assertEquals(List.of(2025, 2026),
                DdAccrualService.yearsSpanned(day(2025, 12, 20), day(2026, 1, 15), zone));
    }

    @Test
    void yearsSpanned_multipleYears() {
        assertEquals(List.of(2024, 2025, 2026),
                DdAccrualService.yearsSpanned(day(2024, 11, 1), day(2026, 2, 1), zone));
    }

    @Test
    void yearsSpanned_invertedWindow_guardsToStartYear() {
        assertEquals(List.of(2026), DdAccrualService.yearsSpanned(day(2026, 6, 1), day(2026, 1, 1), zone));
    }

    @Test
    void yearsSpanned_nullEnd_usesStartYear() {
        assertEquals(List.of(2026), DdAccrualService.yearsSpanned(day(2026, 6, 1), null, zone));
    }
}
