package fr.alb.equipment.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Non-crane yard equipment: internal transport vehicles, reach stackers,
 * forklifts, empty handlers.
 */
@MongoEntity(collection = "YARD_MACHINE")
public class YardMachine extends EntityBase {

    public static final long serialVersionUID = 1L;

    public enum MachineType {
        ITV,            // Internal Transport Vehicle (truck chassis)
        REACH_STACKER,
        FORKLIFT,
        EMPTY_HANDLER,
        TERMINAL_TRACTOR
    }

    public enum Status { IDLE, WORKING, MAINTENANCE, BROKEN_DOWN, DECOMMISSIONED }

    public MachineType type;

    /** Fleet code, e.g. "ITV-12", "RS-03". */
    public String code;

    public String manufacturer;

    public Integer yearOfManufacture;

    /** Lift / carry capacity in tonnes. */
    public Double capacityTonnes;

    public Status status = Status.IDLE;

    public String currentMoveId;

    public String notes;
}
