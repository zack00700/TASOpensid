package fr.alb.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.jboss.logging.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Liveness health check for the application.
 * Monitors application health and resource usage.
 */
@Liveness
@ApplicationScoped
public class ApplicationHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(ApplicationHealthCheck.class);
    private static final double MEMORY_THRESHOLD = 0.95; // 95% memory usage threshold

    @Override
    public HealthCheckResponse call() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        double usage = (double) used / max;

        boolean isHealthy = usage < MEMORY_THRESHOLD;

        HealthCheckResponse response = HealthCheckResponse.named("Application liveness")
            .status(isHealthy)
            .withData("memory_used_mb", used / (1024 * 1024))
            .withData("memory_max_mb", max / (1024 * 1024))
            .withData("memory_usage_percent", Math.round(usage * 100))
            .withData("status", isHealthy ? "healthy" : "memory_critical")
            .build();

        if (!isHealthy) {
            LOG.warnf("Application memory usage critical: %.2f%% (%d MB / %d MB)",
                usage * 100, used / (1024 * 1024), max / (1024 * 1024));
        }

        return response;
    }
}
