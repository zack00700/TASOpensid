package fr.alb.ai.readmodel;

import fr.alb.model.EntityBase;

/**
 * Marker for read-model projections. Exists to make intent obvious at a
 * glance: anything extending {@code ReadModel} is derived from domain events
 * and must never be written to directly from business logic.
 *
 * <p>Query-side only. Rebuildable from the event outbox if dropped.
 */
public abstract class ReadModel extends EntityBase {
    private static final long serialVersionUID = 1L;
}
