package fr.alb.berth.dto;

import java.time.Instant;

/** Payload for {@code POST /berths/allocations}. */
public class AllocationRequest {
    public String berthSlotId;
    public String vesselVisitId;
    public String vesselName;
    public Instant scheduledArrival;
    public Instant scheduledDeparture;
    public String notes;
}
