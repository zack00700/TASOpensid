package fr.alb.yard.model;

import fr.alb.model.EntityBase;

import fr.alb.type.EventScope;
import fr.alb.type.EventType;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "EVENT_CONFIG")
public class EventConfig extends EntityBase {

	private static final long serialVersionUID = 1L;

	private String eventName;
	private EventType eventType;
	private boolean billedEvent;
	private EventScope scope = EventScope.ITEM;

	public EventConfig() {
		super();
	}

	public EventConfig(String eventName, EventType eventType, boolean billedEvent) {
		super();
		this.eventName = eventName;
		this.eventType = eventType;
		this.billedEvent = billedEvent;
	}

	public String getEventName() { return eventName; }
	public void setEventName(String eventName) { this.eventName = eventName; }

	public EventType getEventType() { return eventType; }
	public void setEventType(EventType eventType) { this.eventType = eventType; }

	public boolean isBilledEvent() { return billedEvent; }
	public void setBilledEvent(boolean billedEvent) { this.billedEvent = billedEvent; }

	public EventScope getScope() { return scope == null ? EventScope.ITEM : scope; }
	public void setScope(EventScope scope) { this.scope = scope; }

	@Override
	public String toString() {
		return "EventConfig [eventName=" + eventName + ", eventType=" + eventType + ", billedEvent=" + billedEvent + ", scope=" + scope + "]";
	}
}
