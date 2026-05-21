package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RequiredModules {
	VESSEL_SCHEDULING("Vessel Scheduling"),
	GATE_OPERATIONS("Gate Operations"),
	CUSTOMS_INTERFACE("Customs Interface"),
	YARD_MANAGEMENT("Yard Management"),
	BILLING("Billing"),
	EQUIPEMENT_CONTROL("Equipment Control");
	
	private final String value;
	
	RequiredModules(String value) {
		this.value = value;
	}
	
	@JsonValue
	public String getValue() {
		return value;
	}
	
	@JsonCreator
	public static RequiredModules fromValue(String value) {
		for(RequiredModules rm : RequiredModules.values()) {
			if (rm.value.equalsIgnoreCase(value)) {
				return rm;
			}
		}
		
		throw new IllegalArgumentException("Unknown rm type: " + value);
	}
}
