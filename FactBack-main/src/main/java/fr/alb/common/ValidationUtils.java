package fr.alb.common;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utilitaires de validation centralisés pour éliminer la duplication
 * dans tous les Resources.
 */
public final class ValidationUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private ValidationUtils() {} // Prevent instantiation

    /**
     * Valide qu'un ID n'est pas null/blank et est un UUID valide
     */
    public static void validateId(String id, String fieldName) {
        if (id == null || id.isBlank()) {
            throw new BadRequestException(fieldName + " is required and cannot be blank");
        }

        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(fieldName + " must be a valid UUID");
        }
    }

    /**
     * Valide qu'un objet requis n'est pas null
     */
    public static void validateRequired(Object obj, String fieldName) {
        if (obj == null) {
            throw new BadRequestException(fieldName + " is required");
        }
    }

    /**
     * Valide qu'une chaîne n'est pas null/blank
     */
    public static void validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required and cannot be blank");
        }
    }

    /**
     * Valide qu'une chaîne respecte une longueur maximum
     */
    public static void validateMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new BadRequestException(fieldName + " cannot exceed " + maxLength + " characters");
        }
    }

    /**
     * Valide un format email
     */
    public static void validateEmail(String email) {
        if (email != null && !email.isBlank() && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BadRequestException("Invalid email format");
        }
    }

    /**
     * Valide une pagination
     */
    public static void validatePagination(Integer page, Integer pageSize, int maxPageSize) {
        if (page != null && page < 1) {
            throw new BadRequestException("Page must be >= 1");
        }
        if (pageSize != null && (pageSize < 1 || pageSize > maxPageSize)) {
            throw new BadRequestException("Page size must be between 1 and " + maxPageSize);
        }
    }
}