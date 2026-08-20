package fr.alb.berth.resource;

import fr.alb.berth.model.BerthAllocation;
import fr.alb.berth.model.BerthSlot;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency test for the insert-then-verify guard (M13). Fires many
 * simultaneous allocations on the same berth slot and window and asserts a
 * single one survives — the rest get 400 and leave no row behind.
 *
 * Requires a real MongoDB (runs in CI). It cannot run where MongoDB DevServices
 * / testcontainers is unavailable.
 */
@QuarkusTest
class BerthAllocationConcurrencyTest {

    private String slotId;

    @BeforeEach
    void seed() {
        BerthAllocation.deleteAll();
        BerthSlot.deleteAll();

        BerthSlot slot = new BerthSlot();
        slot.name = "Quai Race";
        slot.active = true;
        slot.persist();
        slotId = slot.id;
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void concurrentAllocations_sameWindow_singleWinner() throws Exception {
        final int threads = 8;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicInteger created = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        final AtomicInteger other = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    int code = given()
                            .contentType(ContentType.JSON)
                            .body(Map.of(
                                "berthSlotId", slotId,
                                "vesselVisitId", "VV-RACE-" + n,
                                "vesselName", "MSC RACE",
                                "scheduledArrival", "2026-08-01T06:00:00Z",
                                "scheduledDeparture", "2026-08-01T18:00:00Z"))
                        .when()
                            .post("/api/berths/allocations")
                        .then()
                            .extract().statusCode();
                    if (code == 201) created.incrementAndGet();
                    else if (code == 400) rejected.incrementAndGet();
                    else other.incrementAndGet();
                } catch (Exception e) {
                    other.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "allocations did not complete in time");
        pool.shutdownNow();

        assertEquals(0, other.get(), "unexpected non-201/400 responses");
        // If this ever observes 2, it is the documented (accepted) stamp/visibility
        // residual in allocate()'s insert-then-verify — not a regression.
        assertEquals(1, created.get(), "exactly one allocation must win the slot");
        assertEquals(threads - 1, rejected.get(), "all other requests must be rejected");

        assertEquals(1, BerthAllocation.count("berthSlotId", slotId),
                "losers must delete their own row — exactly one allocation may remain");
        BerthAllocation survivor = BerthAllocation.find("berthSlotId", slotId).firstResult();
        assertEquals(BerthAllocation.Status.PLANNED, survivor.status);
    }
}
