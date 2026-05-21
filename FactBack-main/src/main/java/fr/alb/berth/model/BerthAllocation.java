package fr.alb.berth.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * A vessel reserved on a {@link BerthSlot} between two timestamps.
 *
 * <p>The allocation starts in {@link Status#PLANNED}. When the vessel
 * actually moors it becomes {@link Status#BERTHED}, and a {@code
 * berth.VesselBerthed} domain event fires — billing then starts the
 * on-quay clock. When the vessel leaves the status moves to {@link
 * Status#DEPARTED} and a {@code berth.VesselDeparted} event closes the
 * session.
 */
@MongoEntity(collection = "BERTH_ALLOCATION")
public class BerthAllocation extends EntityBase {

    public static final long serialVersionUID = 1L;

    public enum Status { PLANNED, BERTHED, DEPARTED, CANCELLED }

    /** FK to {@link BerthSlot#getId()}. */
    public String berthSlotId;

    /** FK to {@code Visit#id} — the vessel visit being served. */
    public String vesselVisitId;

    /** Denormalised for dashboard filters — written by the service layer. */
    public String vesselName;

    /** Planned ETA (overlap detection uses this). */
    public Instant scheduledArrival;

    /** Planned ETD (overlap detection uses this). */
    public Instant scheduledDeparture;

    /** Actual wall-clock time the vessel moored. Null while PLANNED. */
    public Instant actualBerthed;

    /** Actual wall-clock time the vessel left. Null until DEPARTED. */
    public Instant actualDeparted;

    public Status status = Status.PLANNED;

    /** Free-form note from the planner. */
    public String notes;
}
