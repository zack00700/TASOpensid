package fr.alb.yard.lifecycle;

/**
 * Kind of in-yard, mid-lifecycle event.
 *
 * <ul>
 *   <li>{@link #MOVE} — re-positioning inside the yard (no full/empty change).</li>
 *   <li>{@link #DEPOT} — stripping/devanning: full → empty.</li>
 *   <li>{@link #STUFF} — stuffing/empotage: empty → full.</li>
 *   <li>{@link #OTHER} — any in-yard event without state-changing semantics
 *       (inspection, customs hold, damage report, weight verification…).</li>
 * </ul>
 */
public enum IntermediateAction {
    MOVE,
    DEPOT,
    STUFF,
    OTHER
}
