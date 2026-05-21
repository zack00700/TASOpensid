package fr.alb.yard.api;

import fr.alb.type.EventScope;

/**
 * Cross-context API for looking up EventConfig metadata. Other bounded
 * contexts (e.g. berth) consume this rather than importing
 * {@code fr.alb.yard.model.EventConfig} directly, per the architecture
 * rule enforced by {@code BoundedContextArchitectureTest}.
 */
public interface EventConfigApi {

    /**
     * Returns the scope of the EventConfig with the given id, or null if no
     * such config exists.
     */
    EventScope getScope(String eventId);

    /**
     * Returns true if an EventConfig with the given id exists.
     */
    boolean exists(String eventId);
}
