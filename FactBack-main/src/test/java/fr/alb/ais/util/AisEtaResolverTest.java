package fr.alb.ais.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AisEtaResolverTest {

    private static Clock fixed(String iso) {
        return Clock.fixed(Instant.parse(iso), ZoneOffset.UTC);
    }

    @Test
    void resolvesEtaInCurrentYearWhenStillAhead() {
        Optional<Instant> resolved =
            AisEtaResolver.resolve(6, 15, 14, 30, fixed("2026-05-08T07:00:00Z"));
        assertTrue(resolved.isPresent());
        assertEquals(Instant.parse("2026-06-15T14:30:00Z"), resolved.get());
    }

    @Test
    void rollsToNextYearWhenEtaAlreadyPassed() {
        Optional<Instant> resolved =
            AisEtaResolver.resolve(2, 14, 9, 0, fixed("2026-12-30T12:00:00Z"));
        assertTrue(resolved.isPresent());
        assertEquals(Instant.parse("2027-02-14T09:00:00Z"), resolved.get());
    }

    @Test
    void returnsEmptyWhenAnyComponentIsAisSentinel() {
        assertTrue(AisEtaResolver.resolve(0, 15, 14, 30, fixed("2026-05-08T07:00:00Z")).isEmpty());
        assertTrue(AisEtaResolver.resolve(6, 0, 14, 30, fixed("2026-05-08T07:00:00Z")).isEmpty());
        assertTrue(AisEtaResolver.resolve(6, 15, 24, 30, fixed("2026-05-08T07:00:00Z")).isEmpty());
        assertTrue(AisEtaResolver.resolve(6, 15, 14, 60, fixed("2026-05-08T07:00:00Z")).isEmpty());
    }

    @Test
    void returnsEmptyOnNullComponents() {
        assertTrue(AisEtaResolver.resolve(null, 15, 14, 30, fixed("2026-05-08T07:00:00Z")).isEmpty());
    }

    @Test
    void returnsEmptyOnInvalidDateLikeFebruary30() {
        assertTrue(AisEtaResolver.resolve(2, 30, 12, 0, fixed("2026-05-08T07:00:00Z")).isEmpty());
    }
}
