package fr.alb.ai.forecasting;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Linear-regression forecaster over a {@link ThroughputSnapshot} series.
 *
 * <p>Deliberately minimal: {@code y = a + b·x} over the last
 * {@code lookbackMonths} observations, 95% band = ±1.96·σ (residual standard
 * deviation). Good enough to flag capacity squeezes; swap for ARIMA or a
 * seasonal model once real data justifies the complexity.
 */
@ApplicationScoped
public class CapacityForecastService {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * Build a forecast for a single metric.
     *
     * @param metric         which counter to project
     * @param lookbackMonths how many historical months to fit on (min 2)
     * @param horizonMonths  how many future months to project (≥ 0)
     */
    public CapacityForecast forecast(ThroughputSnapshot.Metric metric,
                                     int lookbackMonths,
                                     int horizonMonths) {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        YearMonth oldest = now.minusMonths(Math.max(lookbackMonths, 2) - 1L);

        List<ThroughputSnapshot> rows = ThroughputSnapshot
                .list("metric = ?1 and yearMonth >= ?2 and yearMonth <= ?3",
                        metric.name(), oldest.format(YEAR_MONTH), now.format(YEAR_MONTH));

        rows.sort(Comparator.comparing(r -> r.yearMonth));

        List<String> historicalYm = new ArrayList<>();
        List<Long> historical = new ArrayList<>();
        for (YearMonth ym = oldest; !ym.isAfter(now); ym = ym.plusMonths(1)) {
            historicalYm.add(ym.format(YEAR_MONTH));
            historical.add(find(rows, ym.format(YEAR_MONTH)));
        }

        int n = historical.size();
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = i;
            y[i] = historical.get(i);
        }

        Regression reg = regress(x, y);

        List<String> forecastYm = new ArrayList<>();
        List<Double> forecast = new ArrayList<>();
        List<Double> lower = new ArrayList<>();
        List<Double> upper = new ArrayList<>();
        double band = 1.96d * reg.residualStdDev;
        for (int i = 1; i <= Math.max(horizonMonths, 0); i++) {
            YearMonth future = now.plusMonths(i);
            double xi = n - 1 + i;
            double yi = reg.intercept + reg.slope * xi;
            forecastYm.add(future.format(YEAR_MONTH));
            forecast.add(Math.max(0d, yi));
            lower.add(Math.max(0d, yi - band));
            upper.add(Math.max(0d, yi + band));
        }

        return new CapacityForecast(
                metric.name(),
                historicalYm, historical,
                forecastYm, forecast, lower, upper,
                reg.slope, reg.r2);
    }

    private static long find(List<ThroughputSnapshot> rows, String ym) {
        for (ThroughputSnapshot r : rows) if (ym.equals(r.yearMonth)) return r.value;
        return 0L;
    }

    private static Regression regress(double[] x, double[] y) {
        int n = x.length;
        double sumX = 0, sumY = 0;
        for (int i = 0; i < n; i++) { sumX += x[i]; sumY += y[i]; }
        double meanX = sumX / n, meanY = sumY / n;

        double sxx = 0, sxy = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            sxx += dx * dx;
            sxy += dx * dy;
            syy += dy * dy;
        }
        double slope = sxx == 0 ? 0 : sxy / sxx;
        double intercept = meanY - slope * meanX;

        double ssRes = 0;
        for (int i = 0; i < n; i++) {
            double yi = intercept + slope * x[i];
            ssRes += (y[i] - yi) * (y[i] - yi);
        }
        double r2 = syy == 0 ? 1d : Math.max(0d, 1d - ssRes / syy);
        double resStd = n > 2 ? Math.sqrt(ssRes / (n - 2)) : 0d;

        return new Regression(slope, intercept, r2, resStd);
    }

    private record Regression(double slope, double intercept, double r2, double residualStdDev) {}
}
