package fr.alb.yard.lifecycle;

import static fr.alb.yard.lifecycle.EmptySemantic.EMPTY;
import static fr.alb.yard.lifecycle.EmptySemantic.FULL;
import static fr.alb.yard.lifecycle.IntermediateAction.DEPOT;
import static fr.alb.yard.lifecycle.IntermediateAction.MOVE;
import static fr.alb.yard.lifecycle.IntermediateAction.OTHER;
import static fr.alb.yard.lifecycle.IntermediateAction.STUFF;
import static fr.alb.yard.lifecycle.ItemLifecycleState.DEPARTED;
import static fr.alb.yard.lifecycle.ItemLifecycleState.IN_YARD_EMPTY;
import static fr.alb.yard.lifecycle.ItemLifecycleState.IN_YARD_FULL;
import static fr.alb.yard.lifecycle.ItemLifecycleState.NOT_ARRIVED;
import static fr.alb.yard.lifecycle.LifecycleTransition.in;
import static fr.alb.yard.lifecycle.LifecycleTransition.intermediate;
import static fr.alb.yard.lifecycle.LifecycleTransition.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ItemLifecycleStateMachine}.
 *
 * <p>Organized by source state (one {@code @Nested} class per state) plus
 * one happy-path {@code @Nested} class per business sequence A→E. Tests are
 * pure JUnit 5 — no Quarkus, no Mockito, no DB. The SM is stateless so a
 * single instance is shared across the suite.
 */
class ItemLifecycleStateMachineTest {

    private final ItemLifecycleStateMachine sm = new ItemLifecycleStateMachine();

    /**
     * Asserts that the SM rejects {@code transition} from {@code currentState}
     * consistently — the throwing path ({@link ItemLifecycleStateMachine#nextState})
     * raises {@link InvalidLifecycleTransitionException} with a message that
     * names the transition and the source state, and the predicate path
     * ({@link ItemLifecycleStateMachine#isTransitionAllowed}) returns
     * {@code false}.
     */
    private void assertRejected(ItemLifecycleState currentState,
                                LifecycleTransition transition,
                                String transitionLabel) {
        InvalidLifecycleTransitionException ex = assertThrows(
                InvalidLifecycleTransitionException.class,
                () -> sm.nextState(currentState, transition));
        String msg = ex.getMessage();
        assertTrue(msg.contains(transitionLabel),
                () -> "expected message to contain '" + transitionLabel + "', got: " + msg);
        assertTrue(msg.contains(currentState.name()),
                () -> "expected message to contain '" + currentState.name() + "', got: " + msg);
        assertFalse(sm.isTransitionAllowed(currentState, transition));
    }

    /**
     * Asserts that the SM accepts {@code transition} from {@code fromState}
     * and lands on {@code expectedToState}, consistently across both the
     * throwing path and the predicate path.
     */
    private void assertTransition(ItemLifecycleState fromState,
                                  LifecycleTransition transition,
                                  ItemLifecycleState expectedToState) {
        assertEquals(expectedToState, sm.nextState(fromState, transition));
        assertTrue(sm.isTransitionAllowed(fromState, transition));
    }

    @Nested
    class NotArrivedTransitions {

        @Test
        void shouldRejectOutFullFromNotArrived() {
            assertRejected(NOT_ARRIVED, out(FULL), "OUT+FULL");
        }

        @Test
        void shouldRejectOutEmptyFromNotArrived() {
            assertRejected(NOT_ARRIVED, out(EMPTY), "OUT+EMPTY");
        }

        @Test
        void shouldRejectMoveFromNotArrived() {
            assertRejected(NOT_ARRIVED, intermediate(MOVE), "INTERMEDIATE+MOVE");
        }

        @Test
        void shouldRejectDepotFromNotArrived() {
            assertRejected(NOT_ARRIVED, intermediate(DEPOT), "INTERMEDIATE+DEPOT");
        }

        @Test
        void shouldRejectStuffFromNotArrived() {
            assertRejected(NOT_ARRIVED, intermediate(STUFF), "INTERMEDIATE+STUFF");
        }

        @Test
        void shouldRejectOtherFromNotArrived() {
            assertRejected(NOT_ARRIVED, intermediate(OTHER), "INTERMEDIATE+OTHER");
        }
    }

    @Nested
    class InYardFullTransitions {

        @Test
        void shouldStayInYardFullOnMove() {
            assertTransition(IN_YARD_FULL, intermediate(MOVE), IN_YARD_FULL);
        }

        @Test
        void shouldTransitionFromInYardFullToInYardEmptyOnDepot() {
            assertTransition(IN_YARD_FULL, intermediate(DEPOT), IN_YARD_EMPTY);
        }

        @Test
        void shouldTransitionFromInYardFullToDepartedOnOutFull() {
            assertTransition(IN_YARD_FULL, out(FULL), DEPARTED);
        }

        @Test
        void shouldRejectStuffWhenAlreadyFull() {
            assertRejected(IN_YARD_FULL, intermediate(STUFF), "INTERMEDIATE+STUFF");
        }

        @Test
        void shouldRejectOutEmptyWhenInYardFull() {
            assertRejected(IN_YARD_FULL, out(EMPTY), "OUT+EMPTY");
        }

        @Test
        void shouldStayInYardFullOnOther() {
            assertTransition(IN_YARD_FULL, intermediate(OTHER), IN_YARD_FULL);
        }

        @Test
        void shouldRejectArrivalFullWhenAlreadyInYardFull() {
            assertRejected(IN_YARD_FULL, in(FULL), "IN+FULL");
        }

        @Test
        void shouldRejectArrivalEmptyWhenAlreadyInYardFull() {
            assertRejected(IN_YARD_FULL, in(EMPTY), "IN+EMPTY");
        }
    }

    @Nested
    class InYardEmptyTransitions {

        @Test
        void shouldStayInYardEmptyOnMove() {
            assertTransition(IN_YARD_EMPTY, intermediate(MOVE), IN_YARD_EMPTY);
        }

        @Test
        void shouldTransitionFromInYardEmptyToInYardFullOnStuff() {
            assertTransition(IN_YARD_EMPTY, intermediate(STUFF), IN_YARD_FULL);
        }

        @Test
        void shouldTransitionFromInYardEmptyToDepartedOnOutEmpty() {
            assertTransition(IN_YARD_EMPTY, out(EMPTY), DEPARTED);
        }

        @Test
        void shouldRejectDepotWhenAlreadyEmpty() {
            assertRejected(IN_YARD_EMPTY, intermediate(DEPOT), "INTERMEDIATE+DEPOT");
        }

        @Test
        void shouldRejectOutFullWhenInYardEmpty() {
            assertRejected(IN_YARD_EMPTY, out(FULL), "OUT+FULL");
        }

        @Test
        void shouldStayInYardEmptyOnOther() {
            assertTransition(IN_YARD_EMPTY, intermediate(OTHER), IN_YARD_EMPTY);
        }

        @Test
        void shouldRejectArrivalFullWhenAlreadyInYardEmpty() {
            assertRejected(IN_YARD_EMPTY, in(FULL), "IN+FULL");
        }

        @Test
        void shouldRejectArrivalEmptyWhenAlreadyInYardEmpty() {
            assertRejected(IN_YARD_EMPTY, in(EMPTY), "IN+EMPTY");
        }
    }

    @Nested
    class DepartedTransitions {

        @Test
        void shouldTransitionFromDepartedToInYardFullOnArrivalFull() {
            assertTransition(DEPARTED, in(FULL), IN_YARD_FULL);
        }

        @Test
        void shouldTransitionFromDepartedToInYardEmptyOnArrivalEmpty() {
            assertTransition(DEPARTED, in(EMPTY), IN_YARD_EMPTY);
        }

        @Test
        void shouldRejectMoveWhenDeparted() {
            assertRejected(DEPARTED, intermediate(MOVE), "INTERMEDIATE+MOVE");
        }

        @Test
        void shouldRejectDepotWhenDeparted() {
            assertRejected(DEPARTED, intermediate(DEPOT), "INTERMEDIATE+DEPOT");
        }

        @Test
        void shouldRejectStuffWhenDeparted() {
            assertRejected(DEPARTED, intermediate(STUFF), "INTERMEDIATE+STUFF");
        }

        @Test
        void shouldRejectOtherWhenDeparted() {
            assertRejected(DEPARTED, intermediate(OTHER), "INTERMEDIATE+OTHER");
        }

        @Test
        void shouldRejectOutFullWhenDeparted() {
            assertRejected(DEPARTED, out(FULL), "OUT+FULL");
        }

        @Test
        void shouldRejectOutEmptyWhenDeparted() {
            assertRejected(DEPARTED, out(EMPTY), "OUT+EMPTY");
        }
    }

    @Nested
    class SequenceA_Import {

        /**
         * Import classique : IN+FULL → MOVE×3 → OUT+FULL → re-cycle → IN+EMPTY
         * → MOVE×2 → OUT+EMPTY. Two distinct lifecycles for the same Item.
         */
        @Test
        void shouldCompleteSequenceA_ImportClassique() {
            var state = NOT_ARRIVED;

            // Cycle 1 : arrive full, 3 intra-yard moves, depart full
            state = sm.nextState(state, in(FULL));
            assertEquals(IN_YARD_FULL, state);
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            assertEquals(IN_YARD_FULL, state, "moves must not change state");
            state = sm.nextState(state, out(FULL));
            assertEquals(DEPARTED, state);

            // Cycle 2 : re-cycle empty (the critical DEPARTED → IN_YARD_EMPTY
            // transition validated in Phase 1, ambiguity #1)
            state = sm.nextState(state, in(EMPTY));
            assertEquals(IN_YARD_EMPTY, state);
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            assertEquals(IN_YARD_EMPTY, state);
            state = sm.nextState(state, out(EMPTY));
            assertEquals(DEPARTED, state);
        }
    }

    @Nested
    class SequenceB1_ExportWithStuff {

        /** Export with stuffing on terminal: IN+EMPTY → MOVE×2 → STUFF → MOVE×2 → OUT+FULL. */
        @Test
        void shouldCompleteSequenceB1_ExportWithStuff() {
            var state = NOT_ARRIVED;

            state = sm.nextState(state, in(EMPTY));
            assertEquals(IN_YARD_EMPTY, state);
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            assertEquals(IN_YARD_EMPTY, state);
            state = sm.nextState(state, intermediate(STUFF));
            assertEquals(IN_YARD_FULL, state);
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            assertEquals(IN_YARD_FULL, state);
            state = sm.nextState(state, out(FULL));
            assertEquals(DEPARTED, state);
        }
    }

    @Nested
    class SequenceB2_ExportEmpty {

        /** Export of empty container, no transformation: IN+EMPTY → MOVE×3 → OUT+EMPTY. */
        @Test
        void shouldCompleteSequenceB2_ExportEmpty() {
            var state = NOT_ARRIVED;

            state = sm.nextState(state, in(EMPTY));
            assertEquals(IN_YARD_EMPTY, state);
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            assertEquals(IN_YARD_EMPTY, state);
            state = sm.nextState(state, out(EMPTY));
            assertEquals(DEPARTED, state);
        }
    }

    @Nested
    class SequenceC_Transbordement {

        /** Transbordement: IN+FULL → MOVE×3 → OUT+FULL, container never opened on terminal. */
        @Test
        void shouldCompleteSequenceC_Transbordement() {
            var state = NOT_ARRIVED;

            state = sm.nextState(state, in(FULL));
            assertEquals(IN_YARD_FULL, state);
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            assertEquals(IN_YARD_FULL, state);
            state = sm.nextState(state, out(FULL));
            assertEquals(DEPARTED, state);
        }
    }

    @Nested
    class SequenceD_Depotage {

        /** Dépotage on terminal: IN+FULL → MOVE×2 → DEPOT → MOVE×2 → OUT+EMPTY. */
        @Test
        void shouldCompleteSequenceD_Depotage() {
            var state = NOT_ARRIVED;

            state = sm.nextState(state, in(FULL));
            assertEquals(IN_YARD_FULL, state);
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            assertEquals(IN_YARD_FULL, state);
            state = sm.nextState(state, intermediate(DEPOT));
            assertEquals(IN_YARD_EMPTY, state);
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            assertEquals(IN_YARD_EMPTY, state);
            state = sm.nextState(state, out(EMPTY));
            assertEquals(DEPARTED, state);
        }
    }

    @Nested
    class SequenceE_Empotage {

        /**
         * Empotage on terminal: IN+EMPTY → MOVE×2 → STUFF → MOVE×2 → OUT+FULL.
         *
         * <p>State trace identical to {@link SequenceB1_ExportWithStuff} —
         * intentional. The two sequences differ in business intent (export vs
         * empotage on terminal) but share the same SM path. Both kept for
         * traceability of the audit-listed sequences.
         */
        @Test
        void shouldCompleteSequenceE_Empotage() {
            var state = NOT_ARRIVED;

            state = sm.nextState(state, in(EMPTY));
            assertEquals(IN_YARD_EMPTY, state);
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            assertEquals(IN_YARD_EMPTY, state);
            state = sm.nextState(state, intermediate(STUFF));
            assertEquals(IN_YARD_FULL, state);
            state = sm.nextState(state, intermediate(MOVE));
            state = sm.nextState(state, intermediate(MOVE));
            assertEquals(IN_YARD_FULL, state);
            state = sm.nextState(state, out(FULL));
            assertEquals(DEPARTED, state);
        }
    }
}
