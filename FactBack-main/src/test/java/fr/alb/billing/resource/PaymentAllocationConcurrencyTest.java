package fr.alb.billing.resource;

import fr.alb.billing.model.Invoice;
import fr.alb.billing.model.Payment;
import fr.alb.billing.model.PaymentAllocation;
import fr.alb.type.PaymentMethod;
import fr.alb.type.PaymentStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
 * Concurrency test for the atomic payment-allocation guard (M12). Fires many
 * simultaneous allocations that together exceed the payment amount and asserts
 * the payment is never over-allocated — only as many as fit succeed, the rest
 * get 409.
 *
 * Requires a real MongoDB (runs in CI). It cannot run where MongoDB DevServices
 * / testcontainers is unavailable.
 */
@QuarkusTest
class PaymentAllocationConcurrencyTest {

    private String paymentId;
    private String invoiceId;

    @BeforeEach
    void seed() {
        Payment.deleteAll();
        Invoice.deleteAll();

        // allocate() requires the target invoice to exist.
        Invoice inv = new Invoice();
        inv.status = "DRAFT";
        inv.persist();
        invoiceId = inv.id;

        Payment p = new Payment();
        p.customerId = "CUST-CC";
        p.amount = new BigDecimal("100.00");
        p.unallocatedAmount = new BigDecimal("100.00");
        p.paymentMethod = PaymentMethod.WIRE_TRANSFER;
        p.status = PaymentStatus.PENDING;
        p.persist();
        paymentId = p.id;
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void concurrentAllocations_neverOverAllocate() throws Exception {
        final int threads = 10;              // 10 × 20.00 = 200.00, only 5 fit into 100.00
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
                            .contentType(ContentType.JSON)
                            .body(Map.of("invoiceId", invoiceId, "amount", 20.00))
                        .when()
                            .post("/api/payments/" + paymentId + "/allocate")
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
        startGate.countDown(); // release all threads at once
        assertTrue(done.await(60, TimeUnit.SECONDS), "allocations did not complete in time");
        pool.shutdownNow();

        assertEquals(0, other.get(), "unexpected non-200/409 responses");
        assertEquals(5, ok.get(), "exactly five 20.00 allocations fit into 100.00");
        assertEquals(5, conflict.get(), "the remaining five must be rejected as over-allocation");

        Payment after = Payment.findById(paymentId);
        BigDecimal allocated = after.allocations == null ? BigDecimal.ZERO
                : after.allocations.stream()
                    .map(PaymentAllocation::getAllocatedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(allocated.compareTo(new BigDecimal("100.00")) <= 0,
                "payment must never be over-allocated, was " + allocated);
        assertEquals(0, after.unallocatedAmount.compareTo(BigDecimal.ZERO),
                "running balance must land exactly at zero, was " + after.unallocatedAmount);
    }
}
