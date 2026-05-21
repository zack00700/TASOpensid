package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FeatureRequestStatus {
    DRAFT("DRAFT"),
    CLARIFYING("CLARIFYING"),
    READY_FOR_REVIEW("READY_FOR_REVIEW"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    IN_PROGRESS("IN_PROGRESS"),
    DONE("DONE");

    private final String value;
    FeatureRequestStatus(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static FeatureRequestStatus fromValue(String v) {
        if (v == null) return DRAFT;
        for (FeatureRequestStatus s : values()) {
            if (s.value.equalsIgnoreCase(v) || s.name().equalsIgnoreCase(v)) return s;
        }
        return DRAFT;
    }
}
