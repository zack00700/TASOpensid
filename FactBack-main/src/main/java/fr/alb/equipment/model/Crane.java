package fr.alb.equipment.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Crane master data — quay cranes (QC), rubber-tyred gantries (RTG),
 * rail-mounted gantries (RMG) and mobile harbour cranes (MHC).
 *
 * <p>Tracked for allocation to moves, productivity KPIs (TEU/hour),
 * utilisation rates and maintenance scheduling (Kim ch. 10).
 */
@MongoEntity(collection = "CRANE")
public class Crane extends EntityBase {

    public static final long serialVersionUID = 1L;

    public enum CraneType { QC, RTG, RMG, MHC }

    public enum Status {
        IDLE,           // available for assignment
        WORKING,        // currently executing a move
        MAINTENANCE,    // scheduled downtime
        BROKEN_DOWN,    // unplanned outage
        DECOMMISSIONED  // removed from the fleet
    }

    public CraneType type;

    /** Fleet code, e.g. "QC-01", "RTG-A3". */
    public String code;

    /** Manufacturer display name. */
    public String manufacturer;

    public Integer yearOfManufacture;

    /** Lift capacity in tonnes. Used to guard heavy-cargo assignments. */
    public Double capacityTonnes;

    /**
     * For yard cranes (RTG/RMG): the {@code YardBlock} code they serve.
     * For QC/MHC: the berth code.
     */
    public String assignedArea;

    public Status status = Status.IDLE;

    /** If set, the move currently executed. */
    public String currentMoveId;

    /** Free-form ops notes. */
    public String notes;
}
