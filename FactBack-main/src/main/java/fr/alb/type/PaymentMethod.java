package fr.alb.type;

import fr.alb.common.EnumUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethod implements EnumUtils.ValuedEnum {
    WIRE_TRANSFER("Wire Transfer"),
    CHECK("Check"),
    CASH("Cash"),
    CREDIT_CARD("Credit Card"),
    DIRECT_DEBIT("Direct Debit"),
    ACH("ACH"),
    CRYPTO("Crypto"),
    OTHER("Other");

    private final String value;

    PaymentMethod(String value) {
        this.value = value;
    }

    @JsonValue
    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PaymentMethod fromValue(String value) {
        return EnumUtils.fromValue(value, PaymentMethod.values(), "PaymentMethod");
    }

    public static boolean isValid(String value) {
        return EnumUtils.isValidValue(value, PaymentMethod.values());
    }
}
