package fr.alb.yard.event;

import fr.alb.platform.event.DomainEvent;
import fr.alb.yard.model.ContainerMove;

import java.time.Instant;

/**
 * Fired on every intra-yard re-positioning of an item.
 *
 * <p>Listeners: billing (bill the move according to the rate card),
 * Ask AI projections.
 */
public class ContainerMoved implements DomainEvent {

    public final String itemId;
    public final String fromSlotId;
    public final String toSlotId;
    public final ContainerMove.MoveReason reason;
    public final Instant movedAt;

    public ContainerMoved(String itemId, String fromSlotId, String toSlotId,
                          ContainerMove.MoveReason reason, Instant movedAt) {
        this.itemId = itemId;
        this.fromSlotId = fromSlotId;
        this.toSlotId = toSlotId;
        this.reason = reason;
        this.movedAt = movedAt != null ? movedAt : Instant.now();
    }

    @Override public String eventType()   { return "yard.ContainerMoved"; }
    @Override public String aggregateId() { return itemId; }
    @Override public Instant occurredAt() { return movedAt; }
}
