package fr.alb.type;

import java.util.Arrays;

/**
 * Typed enum replacing magic string subType values scattered across the codebase.
 * Eliminates silent failures from string typos.
 */
public enum CalculationSubType {
    IN_DATE("in_date"),
    DATE_BY_TEU("date_by_teu"),
    BL_VOLUME("bl_volume"),
    BL_WEIGHT("bl_weight"),
    NONE(null);

    public final String value;

    CalculationSubType(String value) {
        this.value = value;
    }

    public static CalculationSubType from(String raw) {
        if (raw == null) return NONE;
        return Arrays.stream(values())
            .filter(s -> s.value != null && s.value.equalsIgnoreCase(raw))
            .findFirst()
            .orElse(NONE);
    }
}
