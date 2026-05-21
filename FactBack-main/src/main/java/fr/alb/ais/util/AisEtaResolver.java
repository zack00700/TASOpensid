package fr.alb.ais.util;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Resolves AIS ETA components (Month, Day, Hour, Minute — no year, UTC) into a concrete Instant
 * by inferring the year. AIS protocol sentinels: month=0, day=0, hour=24, minute=60 mean
 * "not available" — return empty.
 *
 * Year inference: pick the year (current or next) that produces an Instant >= now. If the
 * computed date for the current year is in the past, roll to the next year.
 */
public final class AisEtaResolver {

    private AisEtaResolver() {}

    public static Optional<Instant> resolve(Integer month, Integer day, Integer hour, Integer minute, Clock clock) {
        if (month == null || day == null || hour == null || minute == null) return Optional.empty();
        if (month == 0 || day == 0 || hour == 24 || minute == 60) return Optional.empty();

        Instant now = clock.instant();
        int currentYear = LocalDateTime.ofInstant(now, ZoneOffset.UTC).getYear();

        Optional<Instant> thisYear = tryAt(currentYear, month, day, hour, minute);
        if (thisYear.isPresent() && !thisYear.get().isBefore(now)) {
            return thisYear;
        }
        return tryAt(currentYear + 1, month, day, hour, minute);
    }

    private static Optional<Instant> tryAt(int year, int month, int day, int hour, int minute) {
        try {
            return Optional.of(LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
