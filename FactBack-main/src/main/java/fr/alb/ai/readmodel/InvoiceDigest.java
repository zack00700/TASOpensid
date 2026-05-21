package fr.alb.ai.readmodel;

import io.quarkus.mongodb.panache.common.MongoEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One denormalised row per finalised invoice, hydrated from
 * {@code billing.InvoiceFinalized}. This is the projection Ask AI queries
 * when a user asks "top customers last quarter" or "total billed in March".
 *
 * <p>Fields deliberately duplicate what lives in {@code Invoice} — the read
 * model trades storage for query simplicity and decouples reporting from the
 * write-side schema.
 */
@MongoEntity(collection = "READMODEL_INVOICE_DIGEST")
public class InvoiceDigest extends ReadModel {

    private static final long serialVersionUID = 1L;

    /** Source invoice id — used as idempotency key on upserts. */
    public String invoiceId;

    public String finalNumber;

    public String customerId;
    public String customerName;

    public BigDecimal totalAmount;
    public String currency;

    /** When the invoice became final. */
    public Instant finalizedAt;

    /** Year-month bucket (e.g. {@code "2026-04"}) for fast monthly filters. */
    public String yearMonth;
}
