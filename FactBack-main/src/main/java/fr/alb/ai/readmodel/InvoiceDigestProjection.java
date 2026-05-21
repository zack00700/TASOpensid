package fr.alb.ai.readmodel;

import fr.alb.billing.event.InvoiceFinalized;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Maintains {@link InvoiceDigest} and {@link MonthlyRevenueBucket} by
 * observing {@code billing.InvoiceFinalized} events.
 *
 * <p>Failures are logged but never rethrown: a projection that blocks the
 * write path would defeat the whole point of CQRS. If a projection row is
 * lost, replay from {@code DOMAIN_OUTBOX} is the recovery path.
 */
@ApplicationScoped
public class InvoiceDigestProjection {

    private static final Logger LOG = Logger.getLogger(InvoiceDigestProjection.class);
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    void on(@Observes InvoiceFinalized event) {
        try {
            upsertDigest(event);
            incrementMonthlyBucket(event);
        } catch (Exception e) {
            LOG.errorf(e, "Projection failed for %s (invoiceId=%s) — read model may drift until replay",
                    event.eventType(), event.invoiceId);
        }
    }

    private void upsertDigest(InvoiceFinalized event) {
        String yearMonth = yearMonth(event.occurredAt);
        InvoiceDigest existing = InvoiceDigest.find("invoiceId", event.invoiceId).firstResult();
        if (existing != null) {
            existing.finalNumber = event.finalNumber;
            existing.customerId = event.customerId;
            existing.customerName = event.customerName;
            existing.totalAmount = event.totalAmount;
            existing.currency = event.currency;
            existing.finalizedAt = event.occurredAt;
            existing.yearMonth = yearMonth;
            existing.update();
            return;
        }
        InvoiceDigest digest = new InvoiceDigest();
        digest.invoiceId = event.invoiceId;
        digest.finalNumber = event.finalNumber;
        digest.customerId = event.customerId;
        digest.customerName = event.customerName;
        digest.totalAmount = event.totalAmount;
        digest.currency = event.currency;
        digest.finalizedAt = event.occurredAt;
        digest.yearMonth = yearMonth;
        digest.persist();
    }

    private void incrementMonthlyBucket(InvoiceFinalized event) {
        String yearMonth = yearMonth(event.occurredAt);
        String currency = event.currency != null ? event.currency : "EUR";
        BigDecimal amount = event.totalAmount != null ? event.totalAmount : BigDecimal.ZERO;

        MonthlyRevenueBucket bucket = MonthlyRevenueBucket
                .find("yearMonth = ?1 and currency = ?2", yearMonth, currency)
                .firstResult();
        if (bucket == null) {
            bucket = new MonthlyRevenueBucket();
            bucket.yearMonth = yearMonth;
            bucket.currency = currency;
            bucket.totalAmount = amount;
            bucket.invoiceCount = 1;
            bucket.persist();
            return;
        }
        BigDecimal previous = bucket.totalAmount != null ? bucket.totalAmount : BigDecimal.ZERO;
        bucket.totalAmount = previous.add(amount);
        bucket.invoiceCount += 1;
        bucket.update();
    }

    private static String yearMonth(Instant at) {
        Instant effective = at != null ? at : Instant.now();
        return YEAR_MONTH.format(effective.atOffset(ZoneOffset.UTC));
    }
}
