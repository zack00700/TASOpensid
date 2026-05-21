package fr.alb.billing.domain;

import fr.alb.bol.model.BillOfLading;
import fr.alb.billing.model.Contract;
import fr.alb.model.Event;
import fr.alb.yard.model.Item;
import fr.alb.billing.model.RateManagement;

import java.time.LocalDate;

/**
 * Enriched context passed to each ChargeCalculator.
 * Immutable — built once per calculation, shared across strategy chain.
 */
public record BillingContext(
    Item item,
    Contract contract,
    RateManagement selectedRate,
    Event inEvent,
    Event outEvent,           // null for DATE/IN_DATE mode
    LocalDate invoiceDate,
    BillOfLading billOfLading // null when not applicable
) {
    /** Convenience: true when outEvent is available */
    public boolean hasOutEvent() {
        return outEvent != null;
    }

    /** Convenience: true when a BillOfLading is available */
    public boolean hasBillOfLading() {
        return billOfLading != null;
    }
}
