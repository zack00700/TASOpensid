package fr.alb.equipment.api;

import fr.alb.equipment.model.IsoContainerCode;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Public contract of the {@code equipment} context for ISO 6346 code lookups.
 *
 * <p>This is the single touchpoint other contexts (yard, dd, …) are allowed to
 * import; direct references to {@link fr.alb.equipment.model.IsoContainerCode}
 * from another context violate the bounded-context architecture rules in
 * {@code BoundedContextArchitectureTest}.
 *
 * <p>Used by {@link fr.alb.equipment.validation.ContainerTypeValidator} via a
 * predicate (so the validator stays a pure helper testable without CDI).
 */
@ApplicationScoped
public class IsoContainerCodeRegistry {

    /**
     * @return {@code true} iff a non-blank code is present in the
     *         {@code ISO_CONTAINER_CODE} collection (regardless of
     *         {@code isActive}; activeness is a separate concern).
     */
    public boolean contains(String code) {
        if (code == null || code.isBlank()) return false;
        return IsoContainerCode.find("code", code).firstResult() != null;
    }
}
