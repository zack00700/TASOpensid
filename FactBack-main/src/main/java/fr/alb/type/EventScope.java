package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EventScope {
    ITEM("ITEM"),
    VESSEL("VESSEL"),
    BOTH("BOTH");

    private final String value;

    EventScope(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EventScope fromValue(String value) {
        if (value == null || value.isBlank()) return ITEM;
        for (EventScope s : EventScope.values()) {
            if (s.value.equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown EventScope: " + value);
    }
}
