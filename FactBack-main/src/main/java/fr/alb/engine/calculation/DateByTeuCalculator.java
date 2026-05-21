package fr.alb.engine.calculation;

import fr.alb.billing.domain.BillingContext;
import fr.alb.billing.domain.ChargeResult;
import fr.alb.type.CalculationModeType;
import fr.alb.type.CalculationSubType;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Calculates storage charge between IN and OUT events.
 * Formula: days(outDate - inDate) × unitPrice
 */
@ApplicationScoped
public class DateByTeuCalculator implements ChargeCalculator {

    private static final Logger LOGGER = Logger.getLogger(DateByTeuCalculator.class);

    @Override
    public boolean supports(CalculationModeType type, CalculationSubType subType) {
        return type == CalculationModeType.DATE_BY_TEU;
    }

    @Override
    public ChargeResult calculate(BillingContext ctx) {
        if (ctx.inEvent() == null) {
            LOGGER.warnf("[DateByTeuCalc] No IN event for item %s — cannot compute day count, returning zero",
                    ctx.item() != null ? ctx.item().getId() : "null");
            String currency = ctx.selectedRate() != null && ctx.selectedRate().getCurrency() != null
                    ? ctx.selectedRate().getCurrency() : "EUR";
            return ChargeResult.zero(currency);
        }

        // Convert event timestamps (java.util.Date) to LocalDate
        LocalDate inDate = ctx.inEvent().getTimeStamp() != null
            ? ctx.inEvent().getTimeStamp().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            : LocalDate.now();
        LocalDate outDate = ctx.outEvent() != null && ctx.outEvent().getTimeStamp() != null
            ? ctx.outEvent().getTimeStamp().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            : LocalDate.now();

        long days = ChronoUnit.DAYS.between(inDate, outDate);
        if (days < 0) days = 0;

        BigDecimal unitPrice = BigDecimal.valueOf(ctx.selectedRate().getAmount());
        BigDecimal amount = BigDecimal.valueOf(days).multiply(unitPrice);
        String currency = ctx.selectedRate().getCurrency() != null
            ? ctx.selectedRate().getCurrency() : "";

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("inDate", inDate.toString());
        inputs.put("outDate", outDate.toString());
        inputs.put("days", days);
        inputs.put("unitPrice", unitPrice);

        String explanation = days + " days × " + unitPrice + " " + currency + " = " + amount + " " + currency;

        return new ChargeResult(
            amount, BigDecimal.valueOf(days), "DAY", currency,
            "DateByTeuCalculator",
            ctx.contract().id,
            ctx.selectedRate().getRateId(),
            inputs,
            explanation
        );
    }
}
