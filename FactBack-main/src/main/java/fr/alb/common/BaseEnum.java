package fr.alb.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Classe de base pour tous les enums avec sérialisation JSON standardisée.
 * Élimine la duplication du pattern @JsonCreator/@JsonValue dans 10+ enums.
 */
public abstract class BaseEnum {

    protected final String value;

    protected BaseEnum(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Méthode générique pour conversion String -> Enum
     * À utiliser dans chaque enum avec @JsonCreator
     */
    protected static <T extends BaseEnum> T fromValue(String value, T[] values, String enumName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(enumName + " value cannot be null or blank");
        }

        return Arrays.stream(values)
                .filter(e -> e.value.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + enumName + ": " + value));
    }
}