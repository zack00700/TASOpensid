package fr.alb.billing.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Result of a charge calculation. Immutable. Contains full audit trail.
 * Every field used in the calculation is recorded in {@code inputs}.
 */
public record ChargeResult(
    BigDecimal amount,
    BigDecimal quantity,
    String uom,
    String currency,

    // Audit trail — who calculated, with what
    String calculatorUsed,          // e.g. "DateByTeuCalculator"
    String contractId,
    String rateId,
    Map<String, Object> inputs,     // e.g. {inDate, outDate, days, unitPrice}
    String explanation              // human-readable: "5 days × EUR 12.00/day = EUR 60.00"
) {
    /** Zero-amount result for items that don't match any contract. */
    public static ChargeResult zero(String currency) {
        return new ChargeResult(
            BigDecimal.ZERO, BigDecimal.ZERO, "", currency,
            "none", null, null, Map.of(), "No applicable contract found"
        );
    }
}
