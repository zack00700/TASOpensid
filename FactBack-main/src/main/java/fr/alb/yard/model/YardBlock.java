package fr.alb.yard.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.util.EnumSet;

/**
 * A named area of the container yard (e.g. "Block A", "Reefer Zone 1").
 *
 * <p>Blocks carry the coarse-grained constraints: which item categories are
 * allowed (hazmat-only blocks are segregated from general cargo, reefer
 * blocks provide power outlets, OOG blocks have extra clearance). Finer
 * constraints live on each {@link YardSlot} inside the block.
 */
@MongoEntity(collection = "YARD_BLOCK")
public class YardBlock extends EntityBase {

    public static final long serialVersionUID = 1L;

    public enum BlockKind {
        GENERAL,        // Default block — full/empty containers mixed
        REEFER,         // Reefer-only (power outlets required)
        HAZMAT,         // Hazardous cargo — segregated from general
        OOG,            // Out-of-gauge (oversize, heavy)
        EMPTIES,        // Empty container stacking
        BREAKBULK       // Non-container cargo
    }

    /** Display name, e.g. "A1", "Reefer East". */
    public String name;

    /** Short operational code. */
    public String code;

    /** Which kind of cargo this block accepts. */
    public BlockKind kind = BlockKind.GENERAL;

    /** Nominal capacity in TEU slots — used for occupancy dashboards. */
    public Integer capacityTeu;

    /** Whether this block is operational. Inactive blocks cannot receive new allocations. */
    public boolean active = true;

    /**
     * Additional item categories explicitly allowed on this block. Defaults
     * to the matching kind (REEFER kind allows REEFER items, etc.). Ops
     * may widen a GENERAL block to accept, say, empties on demand.
     */
    public EnumSet<BlockKind> allowedCargo;
}
