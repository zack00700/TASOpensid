package fr.alb.engine.calculation;

import fr.alb.billing.domain.BillingContext;
import fr.alb.billing.domain.ChargeResult;
import fr.alb.type.CalculationModeType;
import fr.alb.type.CalculationSubType;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Calculates storage from IN event to invoice date (open-ended storage).
 * Formula: days(invoiceDate - inDate) × unitPrice
 */
@ApplicationScoped
public class DateCalculator implements ChargeCalculator {

    @Override
    public boolean supports(CalculationModeType type, CalculationSubType subType) {
        return type == CalculationModeType.DATE && subType == CalculationSubType.IN_DATE;
    }

    @Override
    public ChargeResult calculate(BillingContext ctx) {
        LocalDate inDate = ctx.inEvent() != null && ctx.inEvent().getTimeStamp() != null
            ? ctx.inEvent().getTimeStamp().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            : ctx.invoiceDate();
        LocalDate endDate = ctx.invoiceDate() != null ? ctx.invoiceDate() : LocalDate.now();

        long days = ChronoUnit.DAYS.between(inDate, endDate);
        if (days < 0) days = 0;

        BigDecimal unitPrice = BigDecimal.valueOf(ctx.selectedRate().getAmount());
        BigDecimal amount = BigDecimal.valueOf(days).multiply(unitPrice);
        String currency = ctx.selectedRate().getCurrency() != null ? ctx.selectedRate().getCurrency() : "";

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("inDate", inDate.toString());
        inputs.put("invoiceDate", endDate.toString());
        inputs.put("days", days);
        inputs.put("unitPrice", unitPrice);

        return new ChargeResult(
            amount, BigDecimal.valueOf(days), "DAY", currency,
            "DateCalculator",
            ctx.contract().id,
            ctx.selectedRate().getRateId(),
            inputs,
            days + " days (IN→invoice) × " + unitPrice + " " + currency + " = " + amount
        );
    }
}
