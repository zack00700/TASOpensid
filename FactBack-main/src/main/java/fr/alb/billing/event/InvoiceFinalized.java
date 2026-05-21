package fr.alb.billing.event;

import fr.alb.platform.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fired when an invoice transitions from DRAFT to FINAL.
 *
 * <p>Listeners: Ask AI projections, customer notifications (future),
 * accounting integration (future).
 */
public class InvoiceFinalized implements DomainEvent {

    public final String invoiceId;
    public final String finalNumber;
    public final String customerId;
    public final String customerName;
    public final BigDecimal totalAmount;
    public final String currency;
    public final Instant occurredAt;

    public InvoiceFinalized(String invoiceId,
                            String finalNumber,
                            String customerId,
                            String customerName,
                            BigDecimal totalAmount,
                            String currency,
                            Instant occurredAt) {
        this.invoiceId = invoiceId;
        this.finalNumber = finalNumber;
        this.customerId = customerId;
        this.customerName = customerName;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }

    @Override
    public String eventType() {
        return "billing.InvoiceFinalized";
    }

    @Override
    public String aggregateId() {
        return invoiceId;
    }

    @Override
    public Instant occurredAt() {
        return occurredAt;
    }
}
