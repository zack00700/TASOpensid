package fr.alb.edi;

import fr.alb.bol.model.BillOfLading;
import fr.alb.edi.model.EdiMessage;
import fr.alb.yard.model.Item;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency test for the atomic EdiMessage claim (M14). Fires many
 * simultaneous process() calls on the same COPRAR message and asserts the
 * BOL and its items are persisted exactly once.
 *
 * Requires a real MongoDB (runs in CI). It cannot run where MongoDB DevServices
 * / testcontainers is unavailable.
 */
@QuarkusTest
class EdiProcessConcurrencyTest {

    private static final String BL_NUMBER = "BL-RACE-1";
    private static final String COPRAR_PAYLOAD =
            "BOL|" + BL_NUMBER + "|MSC ANNA|V001|FRLEH|MACAS|SHIPPER-1|CONSIGNEE-1\n"
            + "CTR|MSCU1234567|40HC|12000|FULL|SEAL1|\n"
            + "CTR|MSCU7654321|20GP|8000|EMPTY||";

    private String messageId;

    @BeforeEach
    void seed() {
        EdiMessage.deleteAll();
        Item.deleteAll();
        BillOfLading.delete("blNumber", BL_NUMBER);

        EdiMessage msg = new EdiMessage();
        msg.direction = EdiMessage.Direction.INBOUND;
        msg.format = EdiMessage.EdiFormat.EDIFACT;
        msg.messageType = "COPRAR";
        msg.partnerId = "PARTNER-1";
        msg.rawPayload = COPRAR_PAYLOAD;
        msg.persist();
        messageId = msg.id;
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void concurrentProcess_sameMessage_persistsOnce() throws Exception {
        final int threads = 8;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    int code = given()
                            .contentType(ContentType.JSON)
                        .when()
                            .post("/api/edi/" + messageId + "/process")
                        .then()
                            .extract().statusCode();
                    if (code != 200) failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "process calls did not complete in time");
        pool.shutdownNow();

        assertEquals(0, failures.get(), "all process calls must return 200");
        assertEquals(1, BillOfLading.count("blNumber", BL_NUMBER),
                "the BOL must be persisted exactly once");
        BillOfLading bol = BillOfLading.find("blNumber", BL_NUMBER).firstResult();
        assertEquals(2, Item.count("billOfLadingId", bol.getId()),
                "the 2 CTR items must be persisted exactly once");

        EdiMessage after = EdiMessage.findById(messageId);
        assertEquals(EdiMessage.EdiStatus.PROCESSED, after.status);
        assertEquals(1, after.attempts, "exactly one claim must have won");
    }
}
