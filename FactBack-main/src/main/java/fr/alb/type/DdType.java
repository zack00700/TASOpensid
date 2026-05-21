package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Demurrage & Detention type.
 */
public enum DdType {

    DEMURRAGE("Demurrage"),
    DETENTION("Detention");

    private final String label;

    DdType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static DdType fromValue(String value) {
        if (value == null) return null;
        for (DdType t : values()) {
            if (t.name().equalsIgnoreCase(value) || t.label.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown DdType: " + value);
    }
}
