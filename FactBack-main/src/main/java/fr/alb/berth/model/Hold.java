package fr.alb.berth.model;

import fr.alb.model.EntityBase;
import fr.alb.type.HoldType;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * A block placed on a {@link Visit}. While at least one Hold is open
 * (releasedAt == null), the visit cannot be advanced to a forward phase
 * (Active → Completed). Cancellation is always allowed.
 */
@MongoEntity(collection = "HOLD")
public class Hold extends EntityBase {

    private static final long serialVersionUID = 1L;

    public String visitId;
    public HoldType type;
    public String reason;

    public Instant openedAt;
    public String openedBy;

    /** Null while the hold is open; set when the hold is released. */
    public Instant releasedAt;
    public String releasedBy;
    public String releaseNotes;

    public boolean isActive() {
        return releasedAt == null;
    }
}
