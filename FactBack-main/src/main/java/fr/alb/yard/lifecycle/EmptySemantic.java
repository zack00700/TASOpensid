package fr.alb.yard.lifecycle;

/**
 * Full/empty business fact carried by a lifecycle transition.
 *
 * <p>Distinct from {@link fr.alb.type.EmptyStatus}: the existing enum models
 * the <em>known state</em> of an Item (with {@code UNKNOWN} for missing data
 * at EDI ingestion). A transition is, by definition, an explicit fact —
 * there is no {@code UNKNOWN} flavour.
 */
public enum EmptySemantic {
    FULL,
    EMPTY
}
