package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle status of a D&D accrual record.
 */
public enum DdAccrualStatus {

    RUNNING("Running"),
    STOPPED("Stopped"),
    INVOICED("Invoiced"),
    WAIVED("Waived"),
    CANCELLED("Cancelled");

    private final String label;

    DdAccrualStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static DdAccrualStatus fromValue(String value) {
        if (value == null) return null;
        for (DdAccrualStatus t : values()) {
            if (t.name().equalsIgnoreCase(value) || t.label.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown DdAccrualStatus: " + value);
    }
}
