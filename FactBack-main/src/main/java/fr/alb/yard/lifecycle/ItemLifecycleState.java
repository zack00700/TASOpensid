package fr.alb.yard.lifecycle;

/**
 * Composite lifecycle state of a yard Item — physical presence + full/empty
 * semantics on a single axis.
 *
 * <p>See {@code docs/glossary/maritime-lifecycle.md} for the business
 * definition of each state and the audit
 * {@code docs/architecture/audit-item-lifecycle-discovery-2026-05-06.md}
 * Section 2 for the rationale of conflating the two axes.
 */
public enum ItemLifecycleState {

    /** No event received. The Item may be pre-advised in MongoDB but has not crossed the gate. */
    NOT_ARRIVED,

    /** On the terminal, container reported full. */
    IN_YARD_FULL,

    /** On the terminal, container reported empty. */
    IN_YARD_EMPTY,

    /**
     * Has left the terminal. Semi-terminal: a fresh {@code IN} event
     * re-cycles into {@code IN_YARD_*} (sequence A's second leg, etc.).
     */
    DEPARTED
}
