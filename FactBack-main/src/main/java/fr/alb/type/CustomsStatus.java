package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CustomsStatus {
    PENDING("Pending"),
    CLEARED("Cleared"),
    HELD("Held"),
    INSPECTED("Inspected"),
    RELEASED("Released"),
    REFUSED("Refused");

    private final String value;

    CustomsStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static CustomsStatus fromValue(String value) {
        for (CustomsStatus customsStatus : CustomsStatus.values()) {
            if (customsStatus.value.equalsIgnoreCase(value)) {
                return customsStatus;
            }
        }
        throw new IllegalArgumentException("Unknown CustomsStatus value: " + value);
    }
}
