package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ItemType {
	CONTAINER("Container"),
	BREAK_BULK("Break Bulk"),
	VEHICLE("Vehicle");
	
	private final String value;
	
	ItemType(String value) {
		this.value = value;
	}
	
	@JsonValue
	public String getValue() {
		return value;
	}
	
	@JsonCreator
	public static ItemType fromValue(String value) {
		for(ItemType itemType : ItemType.values()) {
			if (itemType.value.equalsIgnoreCase(value)) {
				return itemType;
			}
		}
		
		throw new IllegalArgumentException("Unknown identification type: " + value);
	}
}
