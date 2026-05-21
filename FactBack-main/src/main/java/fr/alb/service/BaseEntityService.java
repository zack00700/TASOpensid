package fr.alb.service;

import fr.alb.model.EntityBase;
import java.time.Instant;

/**
 * Base service providing common entity update operations.
 * Handles version increment, timestamp updates, and other common patterns.
 */
public abstract class BaseEntityService<T extends EntityBase> {

    /**
     * Prepares an entity for update by:
     * - Setting the ID from the current entity
     * - Incrementing the version number for optimistic locking
     * - Updating the timestamp
     * - Optionally setting the updatedBy field
     *
     * @param entity the new entity with updated values
     * @param current the current entity from the database
     * @return the entity prepared for update
     */
    protected T prepareForUpdate(T entity, T current) {
        entity.setId(current.getId());
        entity.version = current.version + 1;
        entity.updatedAt = Instant.now();
        // Preserve audit fields from current entity if not explicitly set
        if (entity.createdAt == null) {
            entity.createdAt = current.createdAt;
        }
        if (entity.createdBy == null) {
            entity.createdBy = current.createdBy;
        }
        return entity;
    }

    /**
     * Prepares an entity for update with user tracking.
     *
     * @param entity the new entity with updated values
     * @param current the current entity from the database
     * @param userId the ID of the user making the update
     * @return the entity prepared for update
     */
    protected T prepareForUpdate(T entity, T current, String userId) {
        prepareForUpdate(entity, current);
        entity.updatedBy = userId;
        return entity;
    }
}
