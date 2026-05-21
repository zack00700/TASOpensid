package fr.alb.customs.api;

/**
 * Public contract of the {@code customs} context — the single touchpoint
 * any other context is allowed to import.
 *
 * <p>The gate context calls {@link #isCleared(String)} before issuing a
 * GateOut event so bonded cargo cannot leave the terminal without a
 * cleared declaration.
 */
public interface CustomsClearanceChecker {

    /**
     * @param itemId the {@code Item} about to leave the yard
     * @return {@code true} if the item is covered by at least one
     *         CLEARED declaration; {@code false} otherwise (missing,
     *         DRAFT, SUBMITTED, HELD, REJECTED)
     */
    boolean isCleared(String itemId);

    /** Human-readable reason for the last {@code false} answer. */
    String lastBlockReason(String itemId);
}
