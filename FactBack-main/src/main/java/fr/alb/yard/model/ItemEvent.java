package fr.alb.yard.model;

import fr.alb.model.EntityBase;

import java.time.Instant;

import fr.alb.yard.model.EventConfig;
import fr.alb.type.EventType;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "EVENT")
public class ItemEvent extends EntityBase {
    private static final long serialVersionUID = 1L;

    private String itemId;
    private String eventId;
    private Instant eventDate;

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Instant getEventDate() {
        return eventDate;
    }

    public void setEventDate(Instant eventDate) {
        this.eventDate = eventDate;
    }

    public EventType getEventType() {
        EventConfig cfg = EventConfig.findById(eventId);
        return cfg != null ? cfg.getEventType() : null;
    }
}
