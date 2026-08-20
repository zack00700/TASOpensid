package fr.alb.infrastructure.outbox;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency test for the atomic outbox claim (M15): concurrent claimers
 * never obtain the same event, and stuck PROCESSING rows are recoverable.
 *
 * Requires a real MongoDB (runs in CI). It cannot run where MongoDB DevServices
 * / testcontainers is unavailable.
 */
@QuarkusTest
class OutboxClaimConcurrencyTest {

    private static final int EVENTS = 20;

    @BeforeEach
    void seed() {
        OutboxEvent.deleteAll();
        for (int i = 0; i < EVENTS; i++) {
            OutboxEvent.of("Probe", "AGG-" + i, "test.Probe", "{}").persist();
        }
    }

    @Test
    void concurrentClaims_eachEventClaimedExactlyOnce() throws Exception {
        final int threads = 8;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final ConcurrentLinkedQueue<String> claimedIds = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    OutboxEvent e;
                    while ((e = OutboxEvent.claim()) != null) {
                        claimedIds.add(e.id.toString());
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "claimers did not complete in time");
        pool.shutdownNow();

        assertEquals(EVENTS, claimedIds.size(), "every event must be claimed exactly once");
        assertEquals(EVENTS, new HashSet<>(claimedIds).size(), "no event may be claimed twice");
        assertEquals(EVENTS, OutboxEvent.count("status", "PROCESSING"));
        assertEquals(0, OutboxEvent.count("status", "PENDING"));
    }

    @Test
    void stuckProcessing_isRecoveredThenClaimable() {
        OutboxEvent.deleteAll();
        OutboxEvent stuck = OutboxEvent.of("Probe", "AGG-STUCK", "test.Probe", "{}");
        stuck.status = "PROCESSING";
        stuck.claimedAt = Instant.now().minus(11, ChronoUnit.MINUTES);
        stuck.persist();

        assertNull(OutboxEvent.claim(), "PROCESSING must not be claimable directly");

        long recovered = OutboxEvent.resetStuckProcessing(Instant.now().minus(10, ChronoUnit.MINUTES));
        assertEquals(1, recovered);

        OutboxEvent reclaimed = OutboxEvent.claim();
        assertNotNull(reclaimed, "recovered event must be claimable again");
        assertEquals("AGG-STUCK", reclaimed.aggregateId);
    }
}
