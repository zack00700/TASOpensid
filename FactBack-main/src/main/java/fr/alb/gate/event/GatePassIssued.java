package fr.alb.gate.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/** Fired when a {@code GatePass} has been issued to an approved appointment. */
public class GatePassIssued implements DomainEvent {

    public final String gatePassId;
    public final String appointmentId;
    public final String code;
    public final Instant issuedAt;

    public GatePassIssued(String gatePassId, String appointmentId, String code, Instant issuedAt) {
        this.gatePassId = gatePassId;
        this.appointmentId = appointmentId;
        this.code = code;
        this.issuedAt = issuedAt != null ? issuedAt : Instant.now();
    }

    @Override public String eventType()   { return "gate.GatePassIssued"; }
    @Override public String aggregateId() { return gatePassId; }
    @Override public Instant occurredAt() { return issuedAt; }
}
