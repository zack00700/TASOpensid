package fr.alb.yard.lifecycle;

import java.util.Objects;

/**
 * Sealed value object describing one lifecycle transition.
 *
 * <p>Three exclusive shapes encoded as records — by construction it is
 * impossible to build a "transition with both an empty semantic and an
 * intermediate action", or "an INTERMEDIATE without action".
 *
 * <ul>
 *   <li>{@link Arrival} — an {@code IN} event with explicit full/empty.</li>
 *   <li>{@link Intermediate} — an in-yard event with explicit action.</li>
 *   <li>{@link Departure} — an {@code OUT} event with explicit full/empty.</li>
 * </ul>
 *
 * <p>Use the static factories ({@link #in}, {@link #intermediate},
 * {@link #out}) for readable call-sites.
 */
public sealed interface LifecycleTransition
        permits LifecycleTransition.Arrival,
                LifecycleTransition.Intermediate,
                LifecycleTransition.Departure {

    static LifecycleTransition in(EmptySemantic semantic) {
        return new Arrival(semantic);
    }

    static LifecycleTransition intermediate(IntermediateAction action) {
        return new Intermediate(action);
    }

    static LifecycleTransition out(EmptySemantic semantic) {
        return new Departure(semantic);
    }

    record Arrival(EmptySemantic semantic) implements LifecycleTransition {
        public Arrival {
            Objects.requireNonNull(semantic, "semantic");
        }
    }

    record Intermediate(IntermediateAction action) implements LifecycleTransition {
        public Intermediate {
            Objects.requireNonNull(action, "action");
        }
    }

    record Departure(EmptySemantic semantic) implements LifecycleTransition {
        public Departure {
            Objects.requireNonNull(semantic, "semantic");
        }
    }
}
