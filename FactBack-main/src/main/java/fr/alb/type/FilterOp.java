package fr.alb.type;

/**
 * Operators supported by {@link fr.alb.model.CalcFilter}.
 */
public enum FilterOp {
    /** Equality for all types. */
    EQ,
    /** Less-than comparison for INT and DATE. */
    LT,
    /** Greater-than comparison for INT and DATE. */
    GT,
    /** Between comparison for INT and DATE. */
    BETWEEN
}
