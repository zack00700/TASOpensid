package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EmptyStatus {
    FULL("Full"),
    EMPTY("Empty"),
    UNKNOWN("Unknown");

    private final String value;

    EmptyStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EmptyStatus fromValue(String value) {
        for (EmptyStatus emptyStatus : EmptyStatus.values()) {
            if (emptyStatus.value.equalsIgnoreCase(value)) {
                return emptyStatus;
            }
        }
        throw new IllegalArgumentException("Unknown EmptyStatus value: " + value);
    }
}
