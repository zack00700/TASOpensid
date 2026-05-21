package fr.alb.ai.forecasting;

import java.util.List;

/**
 * Output of {@link CapacityForecastService#forecast}. Carries the metric,
 * the historical series the projection was trained on, and the projected
 * points for the requested horizon.
 *
 * @param metric               metric name (e.g. {@code "YARD_MOVES"})
 * @param historicalYearMonths yearMonth labels for {@link #historicalValues}
 * @param historicalValues     observed counts, oldest → newest
 * @param forecastYearMonths   yearMonth labels for {@link #forecastValues}
 * @param forecastValues       projected point estimates
 * @param forecastLowerBound   lower 95% bound (same indexing as {@code forecastValues})
 * @param forecastUpperBound   upper 95% bound (same indexing as {@code forecastValues})
 * @param slopePerMonth        fitted slope — expected monthly delta
 * @param r2                   coefficient of determination, {@code [0,1]}
 */
public record CapacityForecast(
        String metric,
        List<String> historicalYearMonths,
        List<Long>   historicalValues,
        List<String> forecastYearMonths,
        List<Double> forecastValues,
        List<Double> forecastLowerBound,
        List<Double> forecastUpperBound,
        double slopePerMonth,
        double r2
) {}
