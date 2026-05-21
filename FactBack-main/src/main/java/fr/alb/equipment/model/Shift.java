package fr.alb.equipment.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * One work slot for an {@link Operator}, optionally bound to a specific
 * piece of equipment.
 *
 * <p>Lifecycle: SCHEDULED → STARTED → ENDED, or CANCELLED before start.
 * Shift events feed productivity analytics (labour hours vs moves
 * performed).
 */
@MongoEntity(collection = "SHIFT")
public class Shift extends EntityBase {

    public static final long serialVersionUID = 1L;

    public enum Status { SCHEDULED, STARTED, ENDED, CANCELLED }

    /** FK to {@link Operator#getId()}. */
    public String operatorId;

    /** Denormalised for dashboards. */
    public String operatorName;

    /** Optional equipment assignment for this shift. */
    public String craneId;
    public String yardMachineId;

    public Instant scheduledStart;
    public Instant scheduledEnd;

    public Instant actualStart;
    public Instant actualEnd;

    public Status status = Status.SCHEDULED;

    public String notes;
}
