package fr.alb.engine.calculation;

import fr.alb.billing.domain.BillingContext;
import fr.alb.type.CalculationSubType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Shared utility: resolves the billable quantity from a BillingContext
 * based on the CalculationSubType.
 * Not a CDI bean — called statically by TieredCalculator and BandedCalculator.
 */
public class QuantityResolver {

    private QuantityResolver() {}

    public static BigDecimal resolve(CalculationSubType subType, BillingContext ctx) {
        return switch (subType) {
            case IN_DATE -> {
                LocalDate inDate = ctx.inEvent() != null && ctx.inEvent().getTimeStamp() != null
                    ? ctx.inEvent().getTimeStamp().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    : ctx.invoiceDate();
                LocalDate endDate = ctx.invoiceDate() != null ? ctx.invoiceDate() : LocalDate.now();
                yield BigDecimal.valueOf(Math.max(0, ChronoUnit.DAYS.between(inDate, endDate)));
            }
            case DATE_BY_TEU -> {
                LocalDate inDate = ctx.inEvent() != null && ctx.inEvent().getTimeStamp() != null
                    ? ctx.inEvent().getTimeStamp().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.now();
                LocalDate outDate = ctx.outEvent() != null && ctx.outEvent().getTimeStamp() != null
                    ? ctx.outEvent().getTimeStamp().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.now();
                yield BigDecimal.valueOf(Math.max(0, ChronoUnit.DAYS.between(inDate, outDate)));
            }
            case BL_VOLUME -> ctx.hasBillOfLading() && ctx.billOfLading().getCommodity() != null
                    && ctx.billOfLading().getCommodity().getVolumeM3() != null
                ? BigDecimal.valueOf(ctx.billOfLading().getCommodity().getVolumeM3())
                : BigDecimal.ONE;
            case BL_WEIGHT -> ctx.hasBillOfLading() && ctx.billOfLading().getCommodity() != null
                    && ctx.billOfLading().getCommodity().getWeightKg() != null
                ? BigDecimal.valueOf(ctx.billOfLading().getCommodity().getWeightKg())
                : BigDecimal.ONE;
            default -> BigDecimal.ONE;
        };
    }
}
