package fr.alb.equipment.validation;

import fr.alb.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Validates a container type code against an external "is in registry" predicate.
 * Used by ItemResource and DdRuleResource to gate writes against the ISO Codes
 * Registry (collection ISO_CONTAINER_CODE).
 *
 * Null and blank values are treated as "unspecified" and pass validation —
 * Item.containerType and DdRule.containerTypeCode both treat null as a valid
 * domain state ("any container type" for DdRule, "no type set" for Item).
 */
public final class ContainerTypeValidator {

    private ContainerTypeValidator() {}

    /**
     * @param value         the container type code to validate (nullable)
     * @param isInRegistry  predicate testing whether a non-blank code exists in the registry
     * @return Optional.of(400 Response) if the value is non-blank and not in registry;
     *         Optional.empty() if the value is null/blank or exists in the registry
     */
    public static Optional<Response> validate(String value, Predicate<String> isInRegistry) {
        if (value == null || value.isBlank()) return Optional.empty();
        if (isInRegistry.test(value)) return Optional.empty();
        return Optional.of(Response.status(400)
                .entity(new ErrorResponse(
                        "INVALID_CONTAINER_TYPE",
                        "Container type '" + value + "' is not in the ISO codes registry",
                        400))
                .build());
    }
}
