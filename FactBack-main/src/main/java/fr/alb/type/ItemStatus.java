package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ItemStatus {
	AVAILABLE("Available"),
	IN_USE("In use"),
	MAINTENANCE("Maintenance"),
	OUT_OF_SERVICE("Out of Service");
	
	private final String value;
	
	ItemStatus(String value) {
		this.value = value;
	}
	
	@JsonValue
	public String getValue() {
		return value;
	}
	
	@JsonCreator
	public static ItemStatus fromValue(String value) {
		for(ItemStatus itemStatus : ItemStatus.values()) {
			if (itemStatus.value.equalsIgnoreCase(value)) {
				return itemStatus;
			}
		}
		
		throw new IllegalArgumentException("Unknown itemStatus type: " + value);
	}
}
