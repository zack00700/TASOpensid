package fr.alb.engine.calculation;

import fr.alb.billing.domain.BillingContext;
import fr.alb.billing.domain.ChargeResult;
import fr.alb.billing.model.RateManagement;
import fr.alb.billing.service.RateSelectionService;
import fr.alb.type.CalculationModeType;
import fr.alb.type.CalculationSubType;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiered pricing: sum of (flatCost + qtyInTier × unitCost) for each tier.
 * Supports date, date_by_teu, bl_volume, bl_weight sub-modes.
 */
@ApplicationScoped
public class TieredCalculator implements ChargeCalculator {

    @Override
    public boolean supports(CalculationModeType type, CalculationSubType subType) {
        return type == CalculationModeType.TIERED;
    }

    @Override
    public ChargeResult calculate(BillingContext ctx) {
        BigDecimal quantity = resolveQuantity(ctx);

        // Filter tiers by item category/freightKind so only applicable tiers are summed
        String itemCat = ctx.item() != null && ctx.item().getCategory() != null
                ? ctx.item().getCategory().getValue() : null;
        String itemFk  = ctx.item() != null && ctx.item().getFreightKind() != null
                ? ctx.item().getFreightKind().getValue() : null;
        List<RateManagement> rates = RateSelectionService.filterEligibleRates(ctx.contract().rates, itemCat, itemFk);
        String currency = ctx.selectedRate().getCurrency() != null ? ctx.selectedRate().getCurrency() : "";

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal remaining = quantity;

        for (RateManagement tier : rates) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal tierStart = BigDecimal.valueOf(tier.getStartQuantity());
            BigDecimal tierEnd = BigDecimal.valueOf(tier.getEndQuantity());
            BigDecimal tierSize = tierEnd.subtract(tierStart);
            BigDecimal qtyInTier = remaining.min(tierSize);

            BigDecimal tierCost = BigDecimal.valueOf(tier.getFlatCost())
                .add(qtyInTier.multiply(BigDecimal.valueOf(tier.getAmount())));
            total = total.add(tierCost);
            remaining = remaining.subtract(qtyInTier);
        }

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("quantity", quantity);
        inputs.put("tierCount", rates.size());

        return new ChargeResult(
            total, quantity, resolveUom(ctx), currency,
            "TieredCalculator",
            ctx.contract().id,
            ctx.selectedRate().getRateId(),
            inputs,
            "Tiered: " + quantity + " units across " + rates.size() + " tiers = " + total + " " + currency
        );
    }

    private BigDecimal resolveQuantity(BillingContext ctx) {
        CalculationSubType subType = CalculationSubType.from(
            ctx.contract().calculationMode != null ? ctx.contract().calculationMode.subType : null
        );
        return QuantityResolver.resolve(subType, ctx);
    }

    private String resolveUom(BillingContext ctx) {
        CalculationSubType subType = CalculationSubType.from(
            ctx.contract().calculationMode != null ? ctx.contract().calculationMode.subType : null
        );
        return switch (subType) {
            case BL_VOLUME -> "m3";
            case BL_WEIGHT -> "kg";
            case IN_DATE, DATE_BY_TEU -> "DAY";
            default -> "unit";
        };
    }
}
