package fr.alb.type;

import fr.alb.common.EnumUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Status implements EnumUtils.ValuedEnum {
	ACTIVE("Active"),
	PENDING("Inactive"),
	DRAFT("Draft"),
	DISABLE("Disable");

	private final String value;

	Status(String value) {
		this.value = value;
	}

	@JsonValue
	@Override
	public String getValue() {
		return value;
	}

	@JsonCreator
	public static Status fromValue(String value) {
		return EnumUtils.fromValue(value, Status.values(), "Status");
	}

	public static boolean isValid(String value) {
		return EnumUtils.isValidValue(value, Status.values());
	}
}
