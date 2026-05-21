package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum VesselType {

	CONTAINER_SHIP("Container Ship"),
	BULK_CARRIER("Bulk Carrier"),
	TANKER("Tanker"),
	GENERAL_CARGO("General Cargo"),
	RORO("Ro-Ro"),
	CAR_CARRIER("Car Carrier"),
	LNG_CARRIER("LNG Carrier"),
	CRUISE_SHIP("Cruise Ship"),
	TRAILING_SUCTION("Trailing Suction Hopper Dredger"),
	TUG("TUG");
	private final String value;
	
	VesselType(String value) {
		this.value = value;
	}
	
	@JsonValue
	public String getValue() {
		return value;
	}
	
	@JsonCreator
	public static VesselType fromValue(String value) {
		for(VesselType vesselType : VesselType.values()) {
			if (vesselType.value.equalsIgnoreCase(value)) {
				return vesselType;
			}
		}
		
		throw new IllegalArgumentException("Unknown vessel type: " + value);
	}
}
