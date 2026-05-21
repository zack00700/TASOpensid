package fr.alb.berth.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Physical berth on the quay where a vessel can moor.
 *
 * <p>Master data: changes rarely. A port terminal typically has a small
 * fixed set of berths (5–30). Dimensions drive eligibility checks when
 * allocating a vessel — a 400 m Ultra Large Container Vessel cannot call
 * at a 300 m berth.
 */
@MongoEntity(collection = "BERTH_SLOT")
public class BerthSlot extends EntityBase {

    public static final long serialVersionUID = 1L;

    /** Display name, e.g. "Quai A1", "Berth 3". */
    public String name;

    /** Optional code used by operations (e.g. "QA1"). */
    public String code;

    /** Total quay length available at this berth, in meters. */
    public Double lengthMeters;

    /** Depth of water at the berth at low tide, in meters. */
    public Double depthMeters;

    /** Maximum draft a vessel can have to be eligible (can differ from depth due to safety margin). */
    public Double maxDraftMeters;

    /** Free-text description or ops notes. */
    public String description;

    /** Whether the berth is operational. Inactive berths cannot be allocated. */
    public boolean active = true;
}
