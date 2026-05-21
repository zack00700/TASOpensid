package fr.alb.common;

import java.util.Arrays;

/**
 * Utilitaire pour standardiser la logique des enums avec valeurs String.
 * Élimine la duplication du pattern @JsonCreator dans 10+ enums.
 */
public final class EnumUtils {

    private EnumUtils() {} // Prevent instantiation

    /**
     * Interface à implémenter par les enums avec valeurs String
     */
    public interface ValuedEnum {
        String getValue();
    }

    /**
     * Méthode générique pour conversion String -> Enum
     * À utiliser dans chaque enum avec @JsonCreator
     */
    public static <T extends Enum<T> & ValuedEnum> T fromValue(String value, T[] values, String enumName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(enumName + " value cannot be null or blank");
        }

        String trimmedValue = value.trim();
        return Arrays.stream(values)
                .filter(e -> e.getValue().equalsIgnoreCase(trimmedValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + enumName + ": " + value));
    }

    /**
     * Validation qu'une valeur String correspond à un enum
     */
    public static <T extends Enum<T> & ValuedEnum> boolean isValidValue(String value, T[] values) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String trimmedValue = value.trim();
        return Arrays.stream(values)
                .anyMatch(e -> e.getValue().equalsIgnoreCase(trimmedValue));
    }
}
