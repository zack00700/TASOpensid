package fr.alb.dto;

import fr.alb.yard.model.EventConfig;

/**
 * Utility methods for converting between {@link EventConfig} entities and
 * {@link EventDTO} objects.
 */
public class EventMapper {

    public static EventDTO toDTO(EventConfig event) {
        if (event == null) {
            return null;
        }
        EventDTO dto = new EventDTO();
        dto.id = event.getId();
        dto.eventName = event.getEventName();
        dto.eventType = event.getEventType();
        dto.billedEvent = event.isBilledEvent();
        dto.scope = event.getScope();
        return dto;
    }
}

