package fr.alb.health;

import com.mongodb.client.MongoClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

/**
 * Health check for MongoDB database connectivity.
 * This check verifies that the application can connect to and query the database.
 */
@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(DatabaseHealthCheck.class);

    @Inject
    MongoClient mongoClient;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named("MongoDB connection");

        try {
            // Perform a simple ping to verify database connectivity
            Document ping = new Document("ping", 1);
            mongoClient.getDatabase("admin").runCommand(ping);

            responseBuilder.up()
                .withData("database", "connected")
                .withData("status", "healthy");

            LOG.debug("MongoDB health check: HEALTHY");
        } catch (Exception e) {
            responseBuilder.down()
                .withData("database", "disconnected")
                .withData("status", "unhealthy")
                .withData("error", e.getMessage());

            LOG.errorf(e, "MongoDB health check: UNHEALTHY - %s", e.getMessage());
        }

        return responseBuilder.build();
    }
}
