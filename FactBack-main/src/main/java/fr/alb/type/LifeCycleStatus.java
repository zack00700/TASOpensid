package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LifeCycleStatus {
	IN_PROGRESS("In Progress"),
	COMPLETED("Completed"),
	CANCELLED("Cancelled");
	
	private final String value;

	LifeCycleStatus(String value) {
		this.value = value;
	}
	
	@JsonValue
	public String getValue() {
		return value;
	}
	
	@JsonCreator
	public static LifeCycleStatus fromValue(String value) {
		for(LifeCycleStatus e : LifeCycleStatus.values()) {
			if (e.value.equalsIgnoreCase(value)) {
				return e;
			}
		}
		
		throw new IllegalArgumentException("Unknown LifeCycleStatus: " + value);
	}
}
