package fr.alb.yard.lifecycle;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stateless lifecycle state machine for yard Items.
 *
 * <p>Models the five maritime business sequences captured in the lifecycle
 * discovery audit:
 * <ul>
 *   <li>A — Import classique : {@code IN+FULL → MOVE×N → OUT+FULL → IN+EMPTY → MOVE×N → OUT+EMPTY}</li>
 *   <li>B1 — Export avec empotage : {@code IN+EMPTY → MOVE×N → STUFF → MOVE×N → OUT+FULL}</li>
 *   <li>B2 — Export sans transformation : {@code IN+EMPTY → MOVE×N → OUT+EMPTY}</li>
 *   <li>C — Transbordement : {@code IN+FULL → MOVE×N → OUT+FULL}</li>
 *   <li>D — Dépotage sur terminal : {@code IN+FULL → MOVE×N → DEPOT → MOVE×N → OUT+EMPTY}</li>
 *   <li>E — Empotage sur terminal : {@code IN+EMPTY → MOVE×N → STUFF → MOVE×N → OUT+FULL}</li>
 * </ul>
 *
 * <p><strong>Pure utility class — no CDI annotation.</strong> Decision D5 of
 * the modeling session: this state machine is not yet wired into
 * {@code ItemEventResource} nor {@code ItemResource}; producers must
 * explicitly invoke it once branched in a follow-up session.
 *
 * <p>Stateless, no shared mutable state — safe to share a single instance,
 * or instantiate per call. Promotion to a CDI bean is a one-line change
 * when the time comes.
 *
 * @see "docs/architecture/audit-item-lifecycle-discovery-2026-05-06.md"
 * @see "docs/glossary/maritime-lifecycle.md"
 */
public final class ItemLifecycleStateMachine {

    /**
     * Compute the next state from the current state and a candidate transition.
     *
     * @throws InvalidLifecycleTransitionException if the transition is not
     *     allowed from {@code currentState}; the exception carries the
     *     allowed alternatives for downstream rendering.
     */
    public ItemLifecycleState nextState(
            ItemLifecycleState currentState,
            LifecycleTransition transition) {
        ItemLifecycleState next = compute(currentState, transition);
        if (next == null) {
            throw new InvalidLifecycleTransitionException(
                    currentState, transition, allowedTransitions(currentState));
        }
        return next;
    }

    /**
     * Cheap predicate variant of {@link #nextState} that does not throw.
     */
    public boolean isTransitionAllowed(
            ItemLifecycleState currentState,
            LifecycleTransition transition) {
        return compute(currentState, transition) != null;
    }

    /**
     * Enumerate every transition allowed from {@code currentState}.
     *
     * <p>Useful for HTTP error responses ("you cannot DEPOT here, but you
     * could MOVE or OUT+EMPTY") and for self-documenting tests. Returns an
     * immutable set in a stable iteration order: arrivals first, then
     * departures, then intermediate actions.
     */
    public Set<LifecycleTransition> allowedTransitions(ItemLifecycleState currentState) {
        Set<LifecycleTransition> allowed = new LinkedHashSet<>();
        for (EmptySemantic semantic : EmptySemantic.values()) {
            tryAdd(allowed, currentState, LifecycleTransition.in(semantic));
        }
        for (EmptySemantic semantic : EmptySemantic.values()) {
            tryAdd(allowed, currentState, LifecycleTransition.out(semantic));
        }
        for (IntermediateAction action : IntermediateAction.values()) {
            tryAdd(allowed, currentState, LifecycleTransition.intermediate(action));
        }
        return Set.copyOf(allowed);
    }

    private void tryAdd(Set<LifecycleTransition> sink,
                        ItemLifecycleState currentState,
                        LifecycleTransition candidate) {
        if (compute(currentState, candidate) != null) {
            sink.add(candidate);
        }
    }

    /**
     * Single source of truth for the transition matrix. Returns the next
     * state, or {@code null} if the {@code (state, transition)} pair is
     * forbidden. Public callers go through {@link #nextState} (throws) or
     * {@link #isTransitionAllowed} (boolean) which adapt the {@code null}.
     */
    private ItemLifecycleState compute(
            ItemLifecycleState currentState,
            LifecycleTransition transition) {
        return switch (currentState) {
            case NOT_ARRIVED -> fromNotArrived(transition);
            case IN_YARD_FULL -> fromInYardFull(transition);
            case IN_YARD_EMPTY -> fromInYardEmpty(transition);
            case DEPARTED -> fromDeparted(transition);
        };
    }

    private ItemLifecycleState fromNotArrived(LifecycleTransition transition) {
        return switch (transition) {
            case LifecycleTransition.Arrival a ->
                    a.semantic() == EmptySemantic.FULL
                            ? ItemLifecycleState.IN_YARD_FULL
                            : ItemLifecycleState.IN_YARD_EMPTY;

            // OTHER (and any INTERMEDIATE) from NOT_ARRIVED is forbidden by design.
            // Rationale: events like inspections, customs holds, damage reports
            // only make sense once the container has physically arrived.
            //
            // If a use case emerges where OTHER events arrive before physical
            // gate-in (e.g. PRE_ADVISE, BOOKING, customs declarations submitted
            // in advance), revisit by either:
            //   1. Allowing OTHER as a self-loop on NOT_ARRIVED, OR
            //   2. Introducing a PRE_ADVISED state distinct from NOT_ARRIVED.
            //
            // See docs/glossary/maritime-lifecycle.md for sequence definitions.
            case LifecycleTransition.Intermediate i -> null;

            // OUT before any IN is incoherent in every sequence A-E.
            case LifecycleTransition.Departure d -> null;
        };
    }

    private ItemLifecycleState fromInYardFull(LifecycleTransition transition) {
        return switch (transition) {
            // Already arrived — a second IN would clobber the active lifecycle.
            case LifecycleTransition.Arrival a -> null;

            case LifecycleTransition.Intermediate i -> switch (i.action()) {
                case MOVE, OTHER -> ItemLifecycleState.IN_YARD_FULL;
                case DEPOT -> ItemLifecycleState.IN_YARD_EMPTY;     // sequence D
                case STUFF -> null;                                  // already full
            };

            case LifecycleTransition.Departure d ->
                    d.semantic() == EmptySemantic.FULL
                            ? ItemLifecycleState.DEPARTED            // sequences A (1st leg), C
                            : null;                                  // OUT+EMPTY incoherent
        };
    }

    private ItemLifecycleState fromInYardEmpty(LifecycleTransition transition) {
        return switch (transition) {
            case LifecycleTransition.Arrival a -> null;

            case LifecycleTransition.Intermediate i -> switch (i.action()) {
                case MOVE, OTHER -> ItemLifecycleState.IN_YARD_EMPTY;
                case STUFF -> ItemLifecycleState.IN_YARD_FULL;       // sequences B1, E
                case DEPOT -> null;                                  // already empty
            };

            case LifecycleTransition.Departure d ->
                    d.semantic() == EmptySemantic.EMPTY
                            ? ItemLifecycleState.DEPARTED            // sequences A (2nd leg), B2, D
                            : null;                                  // OUT+FULL incoherent
        };
    }

    private ItemLifecycleState fromDeparted(LifecycleTransition transition) {
        return switch (transition) {
            // Re-cycle: a fresh IN starts a new lifecycle. Required for
            // sequence A's second leg (OUT+FULL → IN+EMPTY). Supported by
            // the existing model where Item.lifeCycles is plural.
            case LifecycleTransition.Arrival a ->
                    a.semantic() == EmptySemantic.FULL
                            ? ItemLifecycleState.IN_YARD_FULL
                            : ItemLifecycleState.IN_YARD_EMPTY;

            case LifecycleTransition.Intermediate i -> null;
            case LifecycleTransition.Departure d -> null;
        };
    }
}
