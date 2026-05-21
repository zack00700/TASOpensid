package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ItemCategory {
    IMPORT("Import"),
    EXPORT("Export"),
    TRANSSHIP("Transship");

    private final String value;

    ItemCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ItemCategory fromValue(String value) {
        for (ItemCategory c : ItemCategory.values()) {
            if (c.value.equalsIgnoreCase(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown ItemCategory: " + value);
    }
}
