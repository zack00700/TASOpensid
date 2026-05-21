package fr.alb.yard.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Audit log of one container movement inside the yard.
 *
 * <p>Every physical move (gate-in → allocation, re-marshalling, loading,
 * gate-out) creates a row. Billing reads this collection to bill moves;
 * Ask AI projects yard productivity KPIs from it.
 */
@MongoEntity(collection = "CONTAINER_MOVE")
public class ContainerMove extends EntityBase {

    public static final long serialVersionUID = 1L;

    public enum MoveReason {
        GATE_IN,        // first placement after gate-in
        RE_MARSHAL,     // re-position inside yard
        LOAD,           // yard → vessel
        DISCHARGE,      // vessel → yard
        GATE_OUT,       // yard → gate (truck pickup)
        INSPECTION,     // to inspection area
        REPOSITION      // operations-driven move (consolidation, etc.)
    }

    public String itemId;
    public String fromSlotId;   // null when the source is outside the yard (gate-in, discharge)
    public String toSlotId;     // null when the destination is outside the yard (gate-out, load)
    public MoveReason reason;
    public Instant movedAt;
    public String operator;     // user or equipment id that performed the move
    public String notes;
}
