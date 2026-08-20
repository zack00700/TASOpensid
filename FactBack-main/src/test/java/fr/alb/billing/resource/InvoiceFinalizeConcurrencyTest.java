package fr.alb.billing.resource;

import fr.alb.billing.model.CalculationMode;
import fr.alb.billing.model.Contract;
import fr.alb.billing.model.Invoice;
import fr.alb.billing.model.RateManagement;
import fr.alb.sequence.model.InvoiceSequence;
import fr.alb.type.CalculationModeType;
import fr.alb.type.EventType;
import fr.alb.type.Status;
import fr.alb.yard.model.EventConfig;
import fr.alb.yard.model.Item;
import fr.alb.yard.model.ItemEvent;
import fr.alb.yard.model.Lifecycle;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency test for the atomic finalization claim (M12). Two simultaneous
 * finalizes of the same DRAFT invoice must produce exactly one FINAL invoice
 * with exactly one final number consumed — never two.
 *
 * Requires a real MongoDB (runs in CI). It cannot run where MongoDB DevServices
 * / testcontainers is unavailable.
 */
@QuarkusTest
class InvoiceFinalizeConcurrencyTest {

    private String itemId;

    @BeforeEach
    void seed() {
        Invoice.deleteAll();
        Item.deleteAll();
        Lifecycle.deleteAll();
        ItemEvent.deleteAll();
        EventConfig.deleteAll();
        Contract.deleteAll();
        InvoiceSequence.deleteAll();

        // A billable item: one IN event ~10 days ago.
        Item item = new Item();
        item.setItemNumber("CNTU-CC-1");
        item.persist();
        itemId = item.id;

        EventConfig cfg = new EventConfig("Gate In", EventType.IN, true);
        cfg.persist();

        ItemEvent ie = new ItemEvent();
        ie.setItemId(itemId);
        ie.setEventId(cfg.getId());
        ie.setEventDate(Instant.now().minus(10, ChronoUnit.DAYS));
        ie.persist();

        Lifecycle lc = new Lifecycle();
        lc.setItemId(itemId);
        lc.getEventIds().add(ie.getId());
        lc.persist();

        // A global, active DATE/in_date contract with a per-day rate valid at any date.
        Contract c = new Contract();
        c.name = "CC Storage";
        c.status = Status.ACTIVE;
        CalculationMode cm = new CalculationMode();
        cm.type = CalculationModeType.DATE;
        cm.subType = "in_date";
        c.calculationMode = cm;
        RateManagement rate = new RateManagement();
        rate.setRateId("cc-rate-1"); // stable id so the finalize tax lookup resolves it
        rate.setStartQuantity(0);
        rate.setEndQuantity(1_000_000);
        rate.setUnitOfMeasurement("DAY");
        rate.setAmount(10.0);
        rate.setCurrency("EUR");
        c.rates = new ArrayList<>(List.of(rate));
        c.persist();

        // The final-number sequence the finalize path draws from.
        InvoiceSequence seq = new InvoiceSequence();
        seq.sequenceId = "INVOICE_FINAL";
        seq.prefix = "INV";
        seq.nextValue = 1L;
        seq.maximumDigits = 5;
        seq.persist();
    }

    private String createDraftInvoice() {
        Invoice inv = new Invoice();
        inv.status = "DRAFT";
        inv.createdDate = LocalDate.now();
        inv.itemIds = new ArrayList<>(List.of(itemId));
        inv.persist();
        return inv.id;
    }

    private long finalSequenceValue() {
        InvoiceSequence seq = InvoiceSequence.<InvoiceSequence>find("sequenceId", "INVOICE_FINAL").firstResult();
        return seq.nextValue;
    }

    /**
     * Fixture self-check: a single finalize of this seed must succeed and assign
     * a final number. If this fails in CI the fixture is not actually billable
     * and the concurrency assertions below would be meaningless.
     */
    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void precondition_singleFinalizeIsBillable() {
        String id = createDraftInvoice();
        given()
        .when()
            .put("/api/invoice/" + id + "/finalize")
        .then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("FINAL"))
            .body("finalNumber", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void concurrentFinalize_onlyOneWins_singleFinalNumberConsumed() throws Exception {
        final String invoiceId = createDraftInvoice();
        final long seqBefore = finalSequenceValue();

        final int threads = 4;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger conflict = new AtomicInteger();
        final AtomicInteger other = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    int code = given()
                        .when()
                            .put("/api/invoice/" + invoiceId + "/finalize")
                        .then()
                            .extract().statusCode();
                    if (code == 200) ok.incrementAndGet();
                    else if (code == 409) conflict.incrementAndGet();
                    else other.incrementAndGet();
                } catch (Exception e) {
                    other.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "finalize did not complete in time");
        pool.shutdownNow();

        assertEquals(0, other.get(), "unexpected non-200/409 responses");
        assertEquals(1, ok.get(), "exactly one finalize must win");
        assertEquals(threads - 1, conflict.get(), "all other finalizes must get 409");

        Invoice after = Invoice.findById(invoiceId);
        assertEquals("FINAL", after.status);
        assertNotNull(after.finalNumber, "the winner assigned a final number");

        // The key invariant: only ONE legal final number was consumed.
        assertEquals(seqBefore + 1, finalSequenceValue(),
                "exactly one final number must be consumed by concurrent finalizes");
    }
}
