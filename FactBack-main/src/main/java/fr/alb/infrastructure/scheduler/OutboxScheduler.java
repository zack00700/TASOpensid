package fr.alb.infrastructure.scheduler;

import com.mongodb.MongoCommandException;
import fr.alb.infrastructure.outbox.OutboxEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;

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
    /** A PROCESSING event claimed longer ago than this is considered crashed. */
    private static final Duration STUCK_TIMEOUT = Duration.ofMinutes(10);

    @Scheduled(every = "${app.outbox.poll-interval:off}", delayed = "10s",
               concurrentExecution = ConcurrentExecution.SKIP)
    @Transactional
    void processOutbox() {
        try {
            long recovered = OutboxEvent.resetStuckProcessing(Instant.now().minus(STUCK_TIMEOUT));
            if (recovered > 0) {
                LOGGER.warnf("OutboxScheduler: recovered %d stuck PROCESSING event(s)", recovered);
            }
            for (int i = 0; i < BATCH_SIZE; i++) {
                OutboxEvent event = OutboxEvent.claim();
                if (event == null) return;
                dispatchAndMark(event);
            }
        } catch (MongoCommandException e) {
            if (e.getErrorCode() == 26) {
                // NamespaceNotFound: OUTBOX_EVENT collection not yet created in Cosmos DB.
                // Will be created automatically on first insert. Skip silently.
                return;
            }
            throw e;
        }
    }

    private void dispatchAndMark(OutboxEvent event) {
        try {
            dispatch(event);
            event.status      = "SENT";
            event.processedAt = Instant.now();
            event.claimedAt   = null;
            event.update();
        } catch (Exception ex) {
            LOGGER.warnf("OutboxScheduler: failed to dispatch event %s (%s): %s",
                event.id, event.eventType, ex.getMessage());
            event.retryCount++;
            event.lastError = ex.getMessage();
            event.status    = event.retryCount >= MAX_RETRIES ? "FAILED" : "PENDING";
            event.claimedAt = null;
            event.update();
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
