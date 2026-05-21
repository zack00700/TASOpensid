package fr.alb.infrastructure.scheduler;

import com.mongodb.MongoCommandException;
import fr.alb.infrastructure.outbox.OutboxEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

/**
 * Polls the OUTBOX_EVENT collection every 30 seconds and dispatches pending events.
 *
 * Current behavior: logs the event and marks it SENT.
 * Future: replace the body of {@link #dispatch(OutboxEvent)} with an EventHub/Kafka producer call.
 * No other code needs to change.
 *
 * Retry policy: up to 3 attempts, then marks FAILED.
 */
@ApplicationScoped
public class OutboxScheduler {

    private static final Logger LOGGER = Logger.getLogger(OutboxScheduler.class);
    private static final int MAX_RETRIES = 3;
    private static final int BATCH_SIZE  = 50;

    @Scheduled(every = "${app.outbox.poll-interval:off}", delayed = "10s")
    @Transactional
    void processOutbox() {
        List<OutboxEvent> pending;
        try {
            pending = OutboxEvent.findPending(BATCH_SIZE);
        } catch (MongoCommandException e) {
            if (e.getErrorCode() == 26) {
                // NamespaceNotFound: OUTBOX_EVENT collection not yet created in Cosmos DB.
                // Will be created automatically on first insert. Skip silently.
                return;
            }
            throw e;
        }
        if (pending.isEmpty()) return;

        LOGGER.debugf("OutboxScheduler: processing %d pending events", pending.size());

        for (OutboxEvent event : pending) {
            try {
                dispatch(event);
                event.status      = "SENT";
                event.processedAt = Instant.now();
                event.update();
            } catch (Exception ex) {
                LOGGER.warnf("OutboxScheduler: failed to dispatch event %s (%s): %s",
                    event.id, event.eventType, ex.getMessage());
                event.retryCount++;
                event.lastError = ex.getMessage();
                event.status    = event.retryCount >= MAX_RETRIES ? "FAILED" : "PENDING";
                event.update();
            }
        }
    }

    /**
     * Dispatches a single outbox event.
     * TODAY: structured log only.
     * FUTURE: replace body with EventHub/Kafka producer — no caller changes needed.
     */
    private void dispatch(OutboxEvent event) {
        LOGGER.infof("[OUTBOX] type=%s aggregate=%s/%s payload-size=%d",
            event.eventType,
            event.aggregateType,
            event.aggregateId,
            event.payload != null ? event.payload.length() : 0);
        // TODO: eventHubProducer.send(event.eventType, event.payload);
    }
}
