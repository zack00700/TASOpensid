package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TicketCategory {
    UI_UX("UI_UX"),
    BILLING("BILLING"),
    EDI("EDI"),
    REPORTING("REPORTING"),
    PERFORMANCE("PERFORMANCE"),
    INTEGRATION("INTEGRATION"),
    OPERATIONS("OPERATIONS"),
    COMPLIANCE("COMPLIANCE"),
    OTHER("OTHER");

    private final String value;
    TicketCategory(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static TicketCategory fromValue(String v) {
        if (v == null) return OTHER;
        for (TicketCategory c : values()) {
            if (c.value.equalsIgnoreCase(v) || c.name().equalsIgnoreCase(v)) return c;
        }
        return OTHER;
    }
}
