package fr.alb.yard.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/**
 * Fired the first time an item lands in a yard slot — typically after a
 * gate-in or a vessel discharge.
 *
 * <p>Listeners: D&D (start the free-days clock), billing (anchor the
 * storage period), Ask AI.
 */
public class ItemArrived implements DomainEvent {

    public final String itemId;
    public final String slotId;
    public final String blockId;
    public final Instant arrivedAt;

    public ItemArrived(String itemId, String slotId, String blockId, Instant arrivedAt) {
        this.itemId = itemId;
        this.slotId = slotId;
        this.blockId = blockId;
        this.arrivedAt = arrivedAt != null ? arrivedAt : Instant.now();
    }

    @Override public String eventType()   { return "yard.ItemArrived"; }
    @Override public String aggregateId() { return itemId; }
    @Override public Instant occurredAt() { return arrivedAt; }
}
