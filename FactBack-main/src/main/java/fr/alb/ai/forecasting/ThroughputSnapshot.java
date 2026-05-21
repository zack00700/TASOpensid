package fr.alb.ai.forecasting;

import fr.alb.ai.readmodel.ReadModel;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * One counter row per (yearMonth, metric). A metric is an opaque string
 * naming a throughput axis — see {@link Metric} for the built-in set.
 *
 * <p>Kept intentionally shapeless (no per-metric columns) so adding a new
 * metric means emitting a new row key, not migrating a schema.
 */
@MongoEntity(collection = "FORECAST_THROUGHPUT_SNAPSHOT")
public class ThroughputSnapshot extends ReadModel {

    private static final long serialVersionUID = 1L;

    /** Built-in metric names. Extend freely — the entity doesn't enforce them. */
    public enum Metric {
        /** Intra-yard container moves (re-handles + outbound staging). */
        YARD_MOVES,
        /** Gate IN events — trucks delivering or picking up. */
        GATE_IN,
        /** Gate OUT events. */
        GATE_OUT,
        /** Distinct vessel calls in the month. */
        VESSEL_CALLS,
        /** Operator shifts started. */
        SHIFTS_STARTED
    }

    /** Bucket key, e.g. {@code "2026-04"}. */
    public String yearMonth;

    /** {@link Metric#name()} — stored as String to allow new metrics without a migration. */
    public String metric;

    /** Running total for this (yearMonth, metric). */
    public long value;
}
