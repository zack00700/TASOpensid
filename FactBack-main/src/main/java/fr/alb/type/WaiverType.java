package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Category of waiver applied to a D&D accrual.
 */
public enum WaiverType {

    FULL("Full"),
    PARTIAL("Partial"),
    FREE_DAYS_EXTENSION("Free Days Extension"),
    RATE_REDUCTION("Rate Reduction");

    private final String label;

    WaiverType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static WaiverType fromValue(String value) {
        if (value == null) return null;
        for (WaiverType t : values()) {
            if (t.name().equalsIgnoreCase(value) || t.label.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown WaiverType: " + value);
    }
}
