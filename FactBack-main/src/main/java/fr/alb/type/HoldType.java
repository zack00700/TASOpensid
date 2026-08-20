package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import fr.alb.common.EnumUtils;

/**
 * Category of a Hold (block) placed on a Visit. The categories follow standard
 * port-operations vocabulary; {@link #OTHER} is the catch-all when no specific
 * category fits.
 */
public enum HoldType implements EnumUtils.ValuedEnum {

    CUSTOMS("Customs"),
    OPERATIONAL("Operational"),
    FINANCIAL("Financial"),
    SECURITY("Security"),
    DOCUMENTATION("Documentation"),
    OTHER("Other");

    private final String value;

    HoldType(String value) {
        this.value = value;
    }

    @JsonValue
    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static HoldType fromValue(String value) {
        return EnumUtils.fromValue(value, HoldType.values(), "HoldType");
    }

    public static boolean isValid(String value) {
        return EnumUtils.isValidValue(value, HoldType.values());
    }
}
