package fr.alb.engine.calculation;

import fr.alb.type.CalculationModeType;
import fr.alb.type.CalculationSubType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Discovers all ChargeCalculator implementations via CDI and routes to the correct one.
 * New calculation modes only need a new @ApplicationScoped class implementing ChargeCalculator.
 */
@ApplicationScoped
public class ChargeCalculatorRegistry {

    private static final Logger LOGGER = Logger.getLogger(ChargeCalculatorRegistry.class);

    @Inject
    Instance<ChargeCalculator> calculators;

    public ChargeCalculator resolve(CalculationModeType type, CalculationSubType subType) {
        for (ChargeCalculator c : calculators) {
            if (c.supports(type, subType)) {
                return c;
            }
        }
        LOGGER.errorf("No ChargeCalculator found for type=%s subType=%s — invoice line will be ZERO. " +
                "Implement a ChargeCalculator for this mode or remove it from active contracts.", type, subType);
        return new ChargeCalculator() {
            @Override public boolean supports(CalculationModeType t, CalculationSubType s) { return false; }
            @Override public fr.alb.billing.domain.ChargeResult calculate(fr.alb.billing.domain.BillingContext ctx) {
                String currency = ctx.selectedRate() != null && ctx.selectedRate().getCurrency() != null
                    ? ctx.selectedRate().getCurrency() : "EUR";
                return fr.alb.billing.domain.ChargeResult.zero(currency);
            }
        };
    }
}
