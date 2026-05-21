package fr.alb.berth.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/**
 * Fired when a planned allocation transitions to BERTHED — the vessel is
 * physically at the quay.
 *
 * <p>Listeners: billing (start on-quay charges), yard (anticipate unloads),
 * Ask AI projections, customer notifications.
 */
public class VesselBerthed implements DomainEvent {

    public final String allocationId;
    public final String vesselVisitId;
    public final String vesselName;
    public final String berthSlotId;
    public final Instant berthedAt;

    public VesselBerthed(String allocationId,
                         String vesselVisitId,
                         String vesselName,
                         String berthSlotId,
                         Instant berthedAt) {
        this.allocationId = allocationId;
        this.vesselVisitId = vesselVisitId;
        this.vesselName = vesselName;
        this.berthSlotId = berthSlotId;
        this.berthedAt = berthedAt != null ? berthedAt : Instant.now();
    }

    @Override
    public String eventType() {
        return "berth.VesselBerthed";
    }

    @Override
    public String aggregateId() {
        return allocationId;
    }

    @Override
    public Instant occurredAt() {
        return berthedAt;
    }
}
