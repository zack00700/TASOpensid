package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BillOfLadingType {
	ORIGINAL("Original"),
	SEAWAY("Seaway"),
	EXPRESS("Express");
	
	private final String value;

	BillOfLadingType(String value) {
		this.value = value;
	}
	
	@JsonValue
	public String getValue() {
		return value;
	}
	
	@JsonCreator
	public static BillOfLadingType fromValue(String value) {
		for(BillOfLadingType blt : BillOfLadingType.values()) {
			if (blt.value.equalsIgnoreCase(value)) {
				return blt;
			}
		}
		
		throw new IllegalArgumentException("Unknown access type: " + value);
	}
}
