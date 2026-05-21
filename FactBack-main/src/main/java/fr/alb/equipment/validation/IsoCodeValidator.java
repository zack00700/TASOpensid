package fr.alb.equipment.validation;

import java.util.regex.Pattern;

/**
 * Pure utility to validate the format of an ISO 6346 4-character code.
 * Accepts uppercase letters and digits only.
 */
public final class IsoCodeValidator {

    private static final Pattern CODE_REGEX = Pattern.compile("^[A-Z0-9]{4}$");

    private IsoCodeValidator() {}

    public static boolean isValidCode(String code) {
        return code != null && CODE_REGEX.matcher(code).matches();
    }
}
