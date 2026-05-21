package fr.alb.equipment.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Canonical ISO 6346 size/type code. Seeded at startup from
 * /seed/iso-container-codes.json; operator can add custom codes
 * (isStandard=false). The 4-character code is the unique business key.
 */
@MongoEntity(collection = "ISO_CONTAINER_CODE")
public class IsoContainerCode extends EntityBase {
    private static final long serialVersionUID = 1L;

    /** ISO 6346 4-character code (e.g. "22G1"). Unique. */
    public String code;
    public String description;

    public Integer lengthFt;
    public Double heightFt;

    /** ISO 6346 type-group letter: G/R/H/U/T/P/V/B/S. */
    public String typeGroup;

    public boolean isReefer;
    public boolean isHazmatCapable;
    public boolean isTank;
    public boolean isOpenTop;

    /** True if loaded from the seed file; false if added by the operator. */
    public boolean isStandard;
    /** Soft-delete / deprecate flag. */
    public boolean isActive = true;

    /** Optional FK (UUID) → ContainerArchetype.id. null = "not yet assigned". */
    public String archetypeId;

    public Integer tareKg;
    public Integer maxPayloadKg;
    public Integer maxGrossKg;

    public IsoContainerCode() {
        super();
    }
}
