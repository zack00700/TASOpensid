package fr.alb.equipment.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/**
 * Fired when a {@code Crane} or {@code YardMachine} moves between
 * operational states (IDLE ↔ WORKING ↔ MAINTENANCE ↔ BROKEN_DOWN).
 *
 * <p>Listeners: utilisation / downtime KPIs, alerting on unplanned
 * outages that block vessel turnaround.
 */
public class EquipmentStatusChanged implements DomainEvent {

    public final String equipmentId;

    /** "CRANE" or "YARD_MACHINE". */
    public final String equipmentKind;

    public final String previousStatus;
    public final String newStatus;
    public final Instant changedAt;

    public EquipmentStatusChanged(String equipmentId, String equipmentKind,
                                  String previousStatus, String newStatus, Instant changedAt) {
        this.equipmentId = equipmentId;
        this.equipmentKind = equipmentKind;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedAt = changedAt != null ? changedAt : Instant.now();
    }

    @Override public String eventType()   { return "equipment.StatusChanged"; }
    @Override public String aggregateId() { return equipmentId; }
    @Override public Instant occurredAt() { return changedAt; }
}
