package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Anchor event that starts the demurrage/detention clock.
 */
public enum DdClockAnchor {

    DISCHARGE("Discharge"),
    GATE_IN("Gate In"),
    DOCS_READY("Docs Ready"),
    CUSTOMS_CLEARED("Customs Cleared");

    private final String label;

    DdClockAnchor(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static DdClockAnchor fromValue(String value) {
        if (value == null) return null;
        for (DdClockAnchor t : values()) {
            if (t.name().equalsIgnoreCase(value) || t.label.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown DdClockAnchor: " + value);
    }
}
