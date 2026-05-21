package fr.alb.gate.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/**
 * Fired when a truck crosses the gate OUT.
 *
 * <p>Yard listens to release the occupied slot; D&D stops the free-days
 * clock on pickup flows.
 */
public class GateOut implements DomainEvent {

    public final String gateEventId;
    public final String appointmentId;
    public final String itemId;
    public final String licensePlate;
    public final Instant leftAt;

    public GateOut(String gateEventId,
                   String appointmentId,
                   String itemId,
                   String licensePlate,
                   Instant leftAt) {
        this.gateEventId = gateEventId;
        this.appointmentId = appointmentId;
        this.itemId = itemId;
        this.licensePlate = licensePlate;
        this.leftAt = leftAt != null ? leftAt : Instant.now();
    }

    @Override public String eventType()   { return "gate.GateOut"; }
    @Override public String aggregateId() { return gateEventId; }
    @Override public Instant occurredAt() { return leftAt; }
}
