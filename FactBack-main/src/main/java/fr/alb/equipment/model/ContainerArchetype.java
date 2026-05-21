package fr.alb.equipment.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Operator-defined billing/grouping bucket for container types. An archetype
 * is referenced by zero-or-more IsoContainerCode entries via archetypeId.
 * Cardinality: 1 archetype → many ISO codes.
 */
@MongoEntity(collection = "CONTAINER_ARCHETYPE")
public class ContainerArchetype extends EntityBase {
    private static final long serialVersionUID = 1L;

    /** Operator-friendly key, unique. Examples: "DRY_20", "REEFER_40HC", "HAZMAT_TANK". */
    public String code;
    public String name;
    public String description;
    public boolean isActive = true;

    public ContainerArchetype() {
        super();
    }
}
