package fr.alb.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import fr.alb.common.EnumUtils;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle phase of a Visit. Transitions are constrained — the normal flow is
 * {@code Created → Active → Completed}, and a visit may be {@code Canceled} from
 * any non-terminal state. {@code Completed} and {@code Canceled} are terminal.
 */
public enum VisitPhase implements EnumUtils.ValuedEnum {

    CREATED("Created"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    CANCELED("Canceled");

    private final String value;

    VisitPhase(String value) {
        this.value = value;
    }

    @JsonValue
    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static VisitPhase fromValue(String value) {
        return EnumUtils.fromValue(value, VisitPhase.values(), "VisitPhase");
    }

    public static boolean isValid(String value) {
        return EnumUtils.isValidValue(value, VisitPhase.values());
    }

    /** Phases this phase can transition to. Terminal phases return an empty set. */
    public Set<VisitPhase> allowedNextPhases() {
        return switch (this) {
            case CREATED -> EnumSet.of(ACTIVE, CANCELED);
            case ACTIVE -> EnumSet.of(COMPLETED, CANCELED);
            case COMPLETED, CANCELED -> EnumSet.noneOf(VisitPhase.class);
        };
    }

    /** The next "forward" phase in the normal lifecycle (no cancel). Null when terminal. */
    public VisitPhase nextNormal() {
        return switch (this) {
            case CREATED -> ACTIVE;
            case ACTIVE -> COMPLETED;
            default -> null;
        };
    }
}
