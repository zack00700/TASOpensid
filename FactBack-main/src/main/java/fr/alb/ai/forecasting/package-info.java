/**
 * Capacity forecasting — projects future demand onto the resources we've
 * committed (berths, yard slots, cranes, labour hours) so operations can
 * plan crew, maintenance windows and equipment acquisition (Kim ch. 8).
 *
 * <p>Lives under {@code ai} because every input it needs is already in the
 * read model: nothing here writes to business entities. Historical snapshots
 * are fed by CDI observers on the same domain events that back the
 * {@link fr.alb.ai.readmodel} projections; forecasts are pure functions over
 * those snapshots.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *     <li>{@link fr.alb.ai.forecasting.ThroughputSnapshot} — one row per
 *         (yearMonth, metric). Incremented by observers on yard / gate /
 *         berth / equipment events.</li>
 *     <li>{@link fr.alb.ai.forecasting.CapacityForecastService} — runs a
 *         simple linear regression on the last 12 months of history and
 *         projects the requested horizon.</li>
 *     <li>{@link fr.alb.ai.forecasting.ForecastingResource} — dashboards pull
 *         {@code /forecasting/throughput?horizonMonths=N}.</li>
 * </ol>
 *
 * <p>The model is intentionally a straight line with a 95% confidence band;
 * seasonality and ARIMA can replace the math inside {@code forecast(...)}
 * without touching the data contract. Volume is modest enough that doing
 * the regression on the request path is acceptable.
 */
package fr.alb.ai.forecasting;
