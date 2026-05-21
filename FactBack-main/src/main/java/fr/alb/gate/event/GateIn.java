package fr.alb.gate.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/**
 * Fired when a truck crosses the gate IN.
 *
 * <p>Yard listens to allocate a slot for incoming containers (drop-off) or
 * prepare the pickup flow. Billing may anchor a gate fee.
 */
public class GateIn implements DomainEvent {

    public final String gateEventId;
    public final String appointmentId;
    public final String itemId;          // when an item is already known
    public final String billOfLadingId;  // when the appointment targets a BOL
    public final String licensePlate;
    public final Instant enteredAt;

    public GateIn(String gateEventId,
                  String appointmentId,
                  String itemId,
                  String billOfLadingId,
                  String licensePlate,
                  Instant enteredAt) {
        this.gateEventId = gateEventId;
        this.appointmentId = appointmentId;
        this.itemId = itemId;
        this.billOfLadingId = billOfLadingId;
        this.licensePlate = licensePlate;
        this.enteredAt = enteredAt != null ? enteredAt : Instant.now();
    }

    @Override public String eventType()   { return "gate.GateIn"; }
    @Override public String aggregateId() { return gateEventId; }
    @Override public Instant occurredAt() { return enteredAt; }
}
