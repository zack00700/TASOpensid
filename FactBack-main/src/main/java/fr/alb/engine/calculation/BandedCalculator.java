package fr.alb.engine.calculation;

import fr.alb.billing.domain.BillingContext;
import fr.alb.billing.domain.ChargeResult;
import fr.alb.billing.model.RateManagement;
import fr.alb.billing.service.RateSelectionService;
import fr.alb.type.CalculationModeType;
import fr.alb.type.CalculationSubType;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Banded pricing: find the band that contains total quantity, apply that band's rate.
 * Formula: flatCost + totalQty × unitCost (for the matching band only).
 */
@ApplicationScoped
public class BandedCalculator implements ChargeCalculator {

    @Override
    public boolean supports(CalculationModeType type, CalculationSubType subType) {
        return type == CalculationModeType.BANDED;
    }

    @Override
    public ChargeResult calculate(BillingContext ctx) {
        CalculationSubType subType = CalculationSubType.from(
            ctx.contract().calculationMode != null ? ctx.contract().calculationMode.subType : null
        );
        BigDecimal quantity = QuantityResolver.resolve(subType, ctx);

        // Filter bands by item category/freightKind so the correct band is matched
        String itemCat = ctx.item() != null && ctx.item().getCategory() != null
                ? ctx.item().getCategory().getValue() : null;
        String itemFk  = ctx.item() != null && ctx.item().getFreightKind() != null
                ? ctx.item().getFreightKind().getValue() : null;
        List<RateManagement> rates = RateSelectionService.filterEligibleRates(ctx.contract().rates, itemCat, itemFk);
        String currency = ctx.selectedRate().getCurrency() != null ? ctx.selectedRate().getCurrency() : "";

        // Find the band that contains total quantity
        RateManagement matchedBand = rates.stream()
            .filter(r -> quantity.compareTo(BigDecimal.valueOf(r.getStartQuantity())) >= 0
                && quantity.compareTo(BigDecimal.valueOf(r.getEndQuantity())) <= 0)
            .max(Comparator.comparingInt(RateManagement::getPriority))
            .orElse(rates.isEmpty() ? null : rates.get(rates.size() - 1));

        BigDecimal amount = BigDecimal.ZERO;
        String bandDesc = "no band matched";
        if (matchedBand != null) {
            amount = BigDecimal.valueOf(matchedBand.getFlatCost())
                .add(quantity.multiply(BigDecimal.valueOf(matchedBand.getAmount())));
            bandDesc = "[" + matchedBand.getStartQuantity() + "–" + matchedBand.getEndQuantity() + "]";
        }

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("quantity", quantity);
        inputs.put("band", bandDesc);

        return new ChargeResult(
            amount, quantity, "unit", currency,
            "BandedCalculator",
            ctx.contract().id,
            ctx.selectedRate().getRateId(),
            inputs,
            "Band " + bandDesc + ": " + quantity + " × rate = " + amount + " " + currency
        );
    }
}
