package fr.alb.billing.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractAddendumTest {

    private ContractAddendum addendum(Instant from, Instant to) {
        ContractAddendum a = new ContractAddendum();
        a.setValidFrom(from);
        a.setValidTo(to);
        return a;
    }

    @Test
    void isActiveAt_returnsTrue_whenInstantIsInsideWindow() {
        ContractAddendum a = addendum(
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-09-01T00:00:00Z"));
        assertTrue(a.isActiveAt(Instant.parse("2026-07-15T00:00:00Z")));
    }

    @Test
    void isActiveAt_returnsFalse_whenInstantIsBeforeValidFrom() {
        ContractAddendum a = addendum(
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-09-01T00:00:00Z"));
        assertFalse(a.isActiveAt(Instant.parse("2026-05-31T23:59:59Z")));
    }

    @Test
    void isActiveAt_returnsFalse_whenInstantIsAtOrAfterValidTo() {
        ContractAddendum a = addendum(
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-09-01T00:00:00Z"));
        // validTo is exclusive per the implementation (!when.isBefore(validTo) → false at equal)
        assertFalse(a.isActiveAt(Instant.parse("2026-09-01T00:00:00Z")));
    }

    @Test
    void isActiveAt_returnsFalse_whenInstantIsNull() {
        ContractAddendum a = addendum(null, null);
        assertFalse(a.isActiveAt(null), "null instant short-circuits to false");
    }
}
