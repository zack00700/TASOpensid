package fr.alb.berth.service;

import fr.alb.berth.event.VesselBerthed;
import fr.alb.berth.event.VesselDeparted;
import fr.alb.berth.model.BerthAllocation;
import fr.alb.berth.model.BerthSlot;
import fr.alb.platform.event.DomainEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates the creation and lifecycle of {@link BerthAllocation} records.
 *
 * <p>The service is intentionally thin — the hard part of the planning
 * problem (Kim, ch. 5) is the overlap detection and the mooring/departure
 * transitions. Optimisation and a Gantt view come later.
 */
@ApplicationScoped
public class BerthAllocationService {

    private static final Logger LOG = Logger.getLogger(BerthAllocationService.class);

    @Inject
    DomainEventPublisher domainEvents;

    /**
     * Reserve a berth for a vessel visit between {@code scheduledArrival}
     * and {@code scheduledDeparture}. Rejects the request if the slot is
     * already busy on that window or if the slot does not exist / is
     * inactive.
     */
    public BerthAllocation allocate(String berthSlotId,
                                    String vesselVisitId,
                                    String vesselName,
                                    Instant scheduledArrival,
                                    Instant scheduledDeparture,
                                    String notes) {
        if (berthSlotId == null || vesselVisitId == null
                || scheduledArrival == null || scheduledDeparture == null) {
            throw new BadRequestException("berthSlotId, vesselVisitId, scheduledArrival and scheduledDeparture are required");
        }
        if (!scheduledDeparture.isAfter(scheduledArrival)) {
            throw new BadRequestException("scheduledDeparture must be after scheduledArrival");
        }

        BerthSlot slot = BerthSlot.findById(berthSlotId);
        if (slot == null) throw new NotFoundException("Berth slot " + berthSlotId + " not found");
        if (!slot.active) throw new BadRequestException("Berth slot " + berthSlotId + " is inactive");

        List<BerthAllocation> conflicts = findOverlaps(berthSlotId, scheduledArrival, scheduledDeparture, null);
        if (!conflicts.isEmpty()) {
            throw new BadRequestException(String.format(
                    "Berth slot %s is already allocated on the requested window (overlaps %d allocation(s), first: %s)",
                    berthSlotId, conflicts.size(), conflicts.get(0).getId()));
        }

        BerthAllocation allocation = new BerthAllocation();
        allocation.berthSlotId = berthSlotId;
        allocation.vesselVisitId = vesselVisitId;
        allocation.vesselName = vesselName;
        allocation.scheduledArrival = scheduledArrival;
        allocation.scheduledDeparture = scheduledDeparture;
        allocation.notes = notes;
        allocation.status = BerthAllocation.Status.PLANNED;
        allocation.persist();

        LOG.infof("Allocated berth %s for vessel visit %s (%s → %s), allocation=%s",
                berthSlotId, vesselVisitId, scheduledArrival, scheduledDeparture, allocation.getId());
        return allocation;
    }

    /**
     * Transition a PLANNED allocation to BERTHED and fire {@code
     * berth.VesselBerthed}. Idempotent: if already BERTHED, returns as-is.
     */
    public BerthAllocation markBerthed(String allocationId, Instant actualBerthed) {
        BerthAllocation allocation = BerthAllocation.findById(allocationId);
        if (allocation == null) throw new NotFoundException("Allocation " + allocationId + " not found");
        if (allocation.status == BerthAllocation.Status.BERTHED) return allocation;
        if (allocation.status != BerthAllocation.Status.PLANNED) {
            throw new BadRequestException("Cannot berth an allocation in status " + allocation.status);
        }

        Instant when = actualBerthed != null ? actualBerthed : Instant.now();
        allocation.actualBerthed = when;
        allocation.status = BerthAllocation.Status.BERTHED;
        allocation.update();

        domainEvents.publish(new VesselBerthed(
                String.valueOf(allocation.getId()),
                allocation.vesselVisitId,
                allocation.vesselName,
                allocation.berthSlotId,
                when));
        return allocation;
    }

    /**
     * Transition a BERTHED allocation to DEPARTED and fire {@code
     * berth.VesselDeparted}. Idempotent.
     */
    public BerthAllocation markDeparted(String allocationId, Instant actualDeparted) {
        BerthAllocation allocation = BerthAllocation.findById(allocationId);
        if (allocation == null) throw new NotFoundException("Allocation " + allocationId + " not found");
        if (allocation.status == BerthAllocation.Status.DEPARTED) return allocation;
        if (allocation.status != BerthAllocation.Status.BERTHED) {
            throw new BadRequestException("Cannot mark departed from status " + allocation.status);
        }

        Instant when = actualDeparted != null ? actualDeparted : Instant.now();
        allocation.actualDeparted = when;
        allocation.status = BerthAllocation.Status.DEPARTED;
        allocation.update();

        domainEvents.publish(new VesselDeparted(
                String.valueOf(allocation.getId()),
                allocation.vesselVisitId,
                allocation.berthSlotId,
                when));
        return allocation;
    }

    /** Cancel a PLANNED allocation. BERTHED / DEPARTED records cannot be cancelled. */
    public BerthAllocation cancel(String allocationId, String reason) {
        BerthAllocation allocation = BerthAllocation.findById(allocationId);
        if (allocation == null) throw new NotFoundException("Allocation " + allocationId + " not found");
        if (allocation.status != BerthAllocation.Status.PLANNED) {
            throw new BadRequestException("Only PLANNED allocations can be cancelled (status=" + allocation.status + ")");
        }
        allocation.status = BerthAllocation.Status.CANCELLED;
        allocation.notes = reason;
        allocation.update();
        return allocation;
    }

    /**
     * Return any existing allocations on {@code berthSlotId} whose schedule
     * overlaps with [{@code from}, {@code to}]. Cancelled and departed
     * allocations are ignored. Pass {@code excludeAllocationId} to skip the
     * allocation being updated.
     */
    public List<BerthAllocation> findOverlaps(String berthSlotId, Instant from, Instant to, String excludeAllocationId) {
        // Two intervals overlap when A.start < B.end && B.start < A.end.
        String query = "berthSlotId = ?1 and status in ?2 and scheduledArrival < ?3 and scheduledDeparture > ?4";
        List<BerthAllocation.Status> active = List.of(BerthAllocation.Status.PLANNED, BerthAllocation.Status.BERTHED);
        List<BerthAllocation> overlaps = BerthAllocation.list(query, berthSlotId, active, to, from);
        if (excludeAllocationId != null) {
            overlaps.removeIf(a -> excludeAllocationId.equals(String.valueOf(a.getId())));
        }
        return overlaps;
    }
}
