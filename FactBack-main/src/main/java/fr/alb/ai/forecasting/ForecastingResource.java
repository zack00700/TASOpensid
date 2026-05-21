package fr.alb.ai.forecasting;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;

/**
 * REST entry point for capacity forecasting dashboards.
 *
 * <ul>
 *     <li>{@code GET /forecasting/metrics} — list built-in metrics.</li>
 *     <li>{@code GET /forecasting/throughput?metric=YARD_MOVES&horizonMonths=6}
 *         — return the historical + forecast series for one metric.</li>
 *     <li>{@code GET /forecasting/overview?horizonMonths=6} — all metrics in
 *         one call, for a multi-chart dashboard.</li>
 * </ul>
 */
@Path("/forecasting")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
public class ForecastingResource {

    @Inject
    CapacityForecastService service;

    @GET
    @Path("metrics")
    public List<String> metrics() {
        List<String> out = new ArrayList<>();
        for (ThroughputSnapshot.Metric m : ThroughputSnapshot.Metric.values()) out.add(m.name());
        return out;
    }

    @GET
    @Path("throughput")
    public CapacityForecast throughput(@QueryParam("metric") String metric,
                                       @QueryParam("lookbackMonths") Integer lookbackMonths,
                                       @QueryParam("horizonMonths") Integer horizonMonths) {
        if (metric == null || metric.isBlank()) {
            throw new BadRequestException("metric is required");
        }
        ThroughputSnapshot.Metric m;
        try {
            m = ThroughputSnapshot.Metric.valueOf(metric.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown metric: " + metric);
        }
        return service.forecast(m,
                lookbackMonths != null ? lookbackMonths : 12,
                horizonMonths != null ? horizonMonths : 6);
    }

    @GET
    @Path("overview")
    public List<CapacityForecast> overview(@QueryParam("lookbackMonths") Integer lookbackMonths,
                                           @QueryParam("horizonMonths") Integer horizonMonths) {
        int lookback = lookbackMonths != null ? lookbackMonths : 12;
        int horizon  = horizonMonths  != null ? horizonMonths  : 6;
        List<CapacityForecast> out = new ArrayList<>();
        for (ThroughputSnapshot.Metric m : ThroughputSnapshot.Metric.values()) {
            out.add(service.forecast(m, lookback, horizon));
        }
        return out;
    }
}
