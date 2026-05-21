package fr.alb.engine.calculation;

import fr.alb.billing.domain.BillingContext;
import fr.alb.billing.domain.ChargeResult;
import fr.alb.type.CalculationModeType;
import fr.alb.type.CalculationSubType;

/**
 * Strategy interface for charge calculation.
 * Each implementation handles one (CalculationModeType × CalculationSubType) combination.
 *
 * Implementations are CDI beans discovered automatically by ChargeCalculatorRegistry.
 */
public interface ChargeCalculator {

    /**
     * Returns true if this calculator handles the given mode+subType combination.
     */
    boolean supports(CalculationModeType type, CalculationSubType subType);

    /**
     * Calculates the charge for the given billing context.
     * Must be pure (no side effects, no DB writes).
     */
    ChargeResult calculate(BillingContext ctx);
}
