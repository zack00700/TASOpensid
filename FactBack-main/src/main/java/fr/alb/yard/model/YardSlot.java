package fr.alb.yard.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * A single position in the yard, identified by block + bay + row + tier.
 *
 * <p>A yard slot is <strong>occupied</strong> when {@link #currentItemId} is
 * non-null. Allocation policy is enforced by
 * {@code YardAllocationService.allocateSlot(...)}.
 *
 * <p>Convention: bay is along-quay (1..N), row is perpendicular (A..Z or
 * 1..M), tier is stacking height (1 = ground).
 */
@MongoEntity(collection = "YARD_SLOT")
public class YardSlot extends EntityBase {

    public static final long serialVersionUID = 1L;

    /** FK to {@link YardBlock#getId()}. */
    public String blockId;

    /** Display code, e.g. "A1-03-B-2" ({block}-{bay}-{row}-{tier}). */
    public String code;

    public Integer bay;
    public String  row;
    public Integer tier;

    /** Whether this slot has reefer power. */
    public boolean reeferReady;

    /** Whether this slot can host an OOG / flat-rack / oversize container. */
    public boolean oogReady;

    /** Maximum container weight this slot can hold, in kg. Null = no cap. */
    public Double maxWeightKg;

    /** If set, this slot currently holds the given item. Null = free. */
    public String currentItemId;

    /** Free = available for allocation. False when under maintenance or reserved. */
    public boolean active = true;
}
