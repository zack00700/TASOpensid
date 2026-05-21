package fr.alb.yard.lifecycle;

import java.util.Set;

/**
 * Thrown by {@link ItemLifecycleStateMachine#nextState} when the requested
 * transition is not allowed from the current state.
 *
 * <p>Carries typed context (current state, attempted transition, allowed
 * transitions from that state) so downstream consumers — REST resources,
 * structured error responses, audit logs — can render a precise message
 * without re-deriving the data.
 */
public class InvalidLifecycleTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ItemLifecycleState currentState;
    private final LifecycleTransition transition;
    private final Set<LifecycleTransition> allowedTransitions;

    public InvalidLifecycleTransitionException(
            ItemLifecycleState currentState,
            LifecycleTransition transition,
            Set<LifecycleTransition> allowedTransitions) {
        super(buildMessage(currentState, transition, allowedTransitions));
        this.currentState = currentState;
        this.transition = transition;
        this.allowedTransitions = allowedTransitions;
    }

    public ItemLifecycleState getCurrentState() {
        return currentState;
    }

    public LifecycleTransition getTransition() {
        return transition;
    }

    public Set<LifecycleTransition> getAllowedTransitions() {
        return allowedTransitions;
    }

    private static String buildMessage(
            ItemLifecycleState currentState,
            LifecycleTransition transition,
            Set<LifecycleTransition> allowedTransitions) {
        return "Transition " + describe(transition)
                + " is not allowed from state " + currentState
                + ". Allowed transitions: " + describeAll(allowedTransitions);
    }

    private static String describe(LifecycleTransition transition) {
        return switch (transition) {
            case LifecycleTransition.Arrival a -> "IN+" + a.semantic();
            case LifecycleTransition.Intermediate i -> "INTERMEDIATE+" + i.action();
            case LifecycleTransition.Departure d -> "OUT+" + d.semantic();
        };
    }

    private static String describeAll(Set<LifecycleTransition> transitions) {
        if (transitions.isEmpty()) {
            return "(none — terminal state)";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (LifecycleTransition t : transitions) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(describe(t));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}
