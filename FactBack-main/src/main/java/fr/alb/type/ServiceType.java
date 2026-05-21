package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Terminal service types following N4 TAS conventions.
 */
public enum ServiceType {

    STORAGE("Storage"),
    HANDLING("Handling"),
    THC("Terminal Handling Charge"),
    DEMURRAGE("Demurrage"),
    DETENTION("Detention"),
    CLEANING("Cleaning"),
    INSPECTION("Inspection"),
    WEIGHING("Weighing"),
    SCANNING("Scanning"),
    REEFER("Reefer/Electricity"),
    HAZMAT("Hazmat Surcharge"),
    OOG("Out of Gauge"),
    ADMIN("Administration Fee"),
    CUSTOMS("Customs Processing"),
    OTHER("Other");

    private final String label;

    ServiceType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static ServiceType fromValue(String value) {
        if (value == null) return null;
        for (ServiceType t : values()) {
            if (t.name().equalsIgnoreCase(value) || t.label.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown ServiceType: " + value);
    }
}
