package fr.alb.yard.api;

import fr.alb.type.EventScope;
import fr.alb.yard.model.EventConfig;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EventConfigApiImpl implements EventConfigApi {

    @Override
    public EventScope getScope(String eventId) {
        if (eventId == null || eventId.isBlank()) return null;
        EventConfig cfg = EventConfig.findById(eventId);
        return cfg == null ? null : cfg.getScope();
    }

    @Override
    public boolean exists(String eventId) {
        if (eventId == null || eventId.isBlank()) return false;
        EventConfig cfg = EventConfig.findById(eventId);
        return cfg != null;
    }
}
