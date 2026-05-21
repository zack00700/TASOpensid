package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Pricing strategy for a rate row, following N4 TAS conventions.
 *
 * SIMPLE   — flat unit price per UoM (e.g. $10/day regardless of quantity)
 * TIERED   — cumulative: every tier's cost is summed (e.g. 1-10 days @ $5, then 11+ @ $3 each)
 * BANDED   — threshold: the rate of the band the total quantity falls in applies to the whole qty
 * VOLUME   — same as BANDED but applied to volume/weight instead of time
 * CUSTOM   — calculation delegated to a custom plugin/strategy
 */
public enum RateType {

    SIMPLE("Simple"),
    TIERED("Tiered"),
    BANDED("Banded"),
    VOLUME("Volume"),
    CUSTOM("Custom");

    private final String label;

    RateType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static RateType fromValue(String value) {
        if (value == null) return SIMPLE;
        for (RateType t : values()) {
            if (t.name().equalsIgnoreCase(value) || t.label.equalsIgnoreCase(value)) {
                return t;
            }
        }
        return SIMPLE;
    }
}
