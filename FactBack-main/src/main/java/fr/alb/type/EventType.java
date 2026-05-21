package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EventType {

	
	IN("IN"),
	OUT("OUT"),
	INTERMEDIATE("INTERMEDIATE");
	
	private final String value;

	EventType(String value) {
		this.value = value;
	}
	
	@JsonValue
	public String getValue() {
		return value;
	}
	
	@JsonCreator
	public static EventType fromValue(String value) {
		for(EventType e : EventType.values()) {
			if (e.value.equalsIgnoreCase(value)) {
				return e;
			}
		}
		
		throw new IllegalArgumentException("Unknown access type: " + value);
	}
}
