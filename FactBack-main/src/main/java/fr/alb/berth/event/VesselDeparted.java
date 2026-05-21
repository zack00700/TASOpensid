package fr.alb.berth.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/**
 * Fired when a BERTHED vessel leaves the quay. Terminates the on-quay
 * billing session started by {@link VesselBerthed}.
 */
public class VesselDeparted implements DomainEvent {

    public final String allocationId;
    public final String vesselVisitId;
    public final String berthSlotId;
    public final Instant departedAt;

    public VesselDeparted(String allocationId,
                          String vesselVisitId,
                          String berthSlotId,
                          Instant departedAt) {
        this.allocationId = allocationId;
        this.vesselVisitId = vesselVisitId;
        this.berthSlotId = berthSlotId;
        this.departedAt = departedAt != null ? departedAt : Instant.now();
    }

    @Override
    public String eventType() {
        return "berth.VesselDeparted";
    }

    @Override
    public String aggregateId() {
        return allocationId;
    }

    @Override
    public Instant occurredAt() {
        return departedAt;
    }
}
