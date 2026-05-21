package fr.alb.type;

import fr.alb.common.EnumUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatus implements EnumUtils.ValuedEnum {
    PENDING("Pending"),
    CLEARED("Cleared"),
    FAILED("Failed"),
    REVERSED("Reversed"),
    CANCELLED("Cancelled");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PaymentStatus fromValue(String value) {
        return EnumUtils.fromValue(value, PaymentStatus.values(), "PaymentStatus");
    }

    public static boolean isValid(String value) {
        return EnumUtils.isValidValue(value, PaymentStatus.values());
    }
}
