package fr.alb.yard.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/**
 * Fired when an item leaves the yard for good — gate-out to truck, loaded
 * on departing vessel, or destroyed/scrapped.
 *
 * <p>Listeners: D&D (stop the clock), billing (finalise storage charges),
 * Ask AI.
 */
public class ItemDelivered implements DomainEvent {

    public final String itemId;
    public final String fromSlotId;
    public final Instant deliveredAt;

    public ItemDelivered(String itemId, String fromSlotId, Instant deliveredAt) {
        this.itemId = itemId;
        this.fromSlotId = fromSlotId;
        this.deliveredAt = deliveredAt != null ? deliveredAt : Instant.now();
    }

    @Override public String eventType()   { return "yard.ItemDelivered"; }
    @Override public String aggregateId() { return itemId; }
    @Override public Instant occurredAt() { return deliveredAt; }
}
