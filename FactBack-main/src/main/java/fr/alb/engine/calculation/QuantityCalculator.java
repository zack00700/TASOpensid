package fr.alb.engine.calculation;

import fr.alb.billing.domain.BillingContext;
import fr.alb.billing.domain.ChargeResult;
import fr.alb.type.CalculationModeType;
import fr.alb.type.CalculationSubType;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Fixed quantity charge. Supports bl_volume and bl_weight sub-modes.
 */
@ApplicationScoped
public class QuantityCalculator implements ChargeCalculator {

    @Override
    public boolean supports(CalculationModeType type, CalculationSubType subType) {
        return type == CalculationModeType.QUANTITY;
    }

    @Override
    public ChargeResult calculate(BillingContext ctx) {
        BigDecimal quantity;
        String uom;
        String quantitySource;

        CalculationSubType subType = CalculationSubType.from(
            ctx.contract().calculationMode != null ? ctx.contract().calculationMode.subType : null
        );

        if (subType == CalculationSubType.BL_VOLUME && ctx.hasBillOfLading()
                && ctx.billOfLading().getCommodity() != null) {
            Double vol = ctx.billOfLading().getCommodity().getVolumeM3();
            quantity = BigDecimal.valueOf(vol != null ? vol : 0.0);
            uom = "m3";
            quantitySource = "bl_volume";
        } else if (subType == CalculationSubType.BL_WEIGHT && ctx.hasBillOfLading()
                && ctx.billOfLading().getCommodity() != null) {
            Double wt = ctx.billOfLading().getCommodity().getWeightKg();
            quantity = BigDecimal.valueOf(wt != null ? wt : 0.0);
            uom = "kg";
            quantitySource = "bl_weight";
        } else {
            quantity = BigDecimal.ONE;
            uom = "unit";
            quantitySource = "fixed";
        }

        BigDecimal unitPrice = BigDecimal.valueOf(ctx.selectedRate().getAmount());
        BigDecimal amount = quantity.multiply(unitPrice);
        String currency = ctx.selectedRate().getCurrency() != null ? ctx.selectedRate().getCurrency() : "";

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("quantity", quantity);
        inputs.put("quantitySource", quantitySource);
        inputs.put("unitPrice", unitPrice);
        inputs.put("uom", uom);

        return new ChargeResult(
            amount, quantity, uom, currency,
            "QuantityCalculator",
            ctx.contract().id,
            ctx.selectedRate().getRateId(),
            inputs,
            quantity + " " + uom + " × " + unitPrice + " " + currency + " = " + amount
        );
    }
}
