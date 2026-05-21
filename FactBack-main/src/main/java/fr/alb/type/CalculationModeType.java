package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CalculationModeType {

	DATE("Date"),
	QUANTITY("Quantity"),
	DATE_BY_TEU("DateByTEU"),
	SPECIAL("Special"),
	TIERED("Tiered"),
	BANDED("Banded");
	
	private final String value;

	CalculationModeType(String value) {
		this.value = value;
	}
	
	@JsonValue
	public String getValue() {
		return value;
	}
	
	@JsonCreator
	public static CalculationModeType fromValue(String value) {
		for(CalculationModeType e : CalculationModeType.values()) {
			if (e.value.equalsIgnoreCase(value)) {
				return e;
			}
		}
		
		throw new IllegalArgumentException("Unknown CalculationModeType : " + value);
	}
}
