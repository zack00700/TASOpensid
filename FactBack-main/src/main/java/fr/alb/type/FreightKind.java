package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Nature du fret — détermine quelle ligne tarifaire s'applique. */
public enum FreightKind {
    FCL("FCL"),        // Full Container Load
    LCL("LCL"),        // Less than Container Load
    EMPTY("Empty"),    // Conteneur vide
    BREAKBULK("Breakbulk"),
    RO_RO("Ro-Ro");    // Roll-on/Roll-off (véhicules)

    private final String value;

    FreightKind(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static FreightKind fromValue(String value) {
        for (FreightKind fk : FreightKind.values()) {
            if (fk.value.equalsIgnoreCase(value)) {
                return fk;
            }
        }
        throw new IllegalArgumentException("Unknown FreightKind: " + value);
    }
}
