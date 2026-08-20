package fr.alb.bol.api;

/**
 * Cross-context API for BillOfLading lookups. Other bounded contexts
 * (e.g. edi) consume this rather than importing
 * {@code fr.alb.bol.model.BillOfLading} directly, per the architecture
 * rule enforced by {@code BoundedContextArchitectureTest}.
 */
public interface BillOfLadingApi {

    /**
     * Returns the id of the non-deleted BillOfLading carrying this
     * blNumber, or null when none exists.
     */
    String findActiveIdByBlNumber(String blNumber);
}
