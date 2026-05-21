package fr.alb.dto;

import fr.alb.type.EventScope;
import fr.alb.type.EventType;

/**
 * Public representation of an event configuration exposed via the API.
 */
public class EventDTO {
    public String id;
    public String eventName;
    public EventType eventType;
    public boolean billedEvent;
    public EventScope scope;
}

