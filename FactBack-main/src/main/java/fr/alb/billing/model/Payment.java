package fr.alb.billing.model;

import fr.alb.model.EntityBase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import fr.alb.type.PaymentMethod;
import fr.alb.type.PaymentStatus;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "PAYMENT")
public class Payment extends EntityBase {

    private static final long serialVersionUID = 1L;

    /** Human-readable unique reference, e.g. PAY-2024-001. */
    public String paymentReference;

    /** Reference to the paying customer. */
    public String customerId;

    /** Denormalized customer name to avoid extra lookups. */
    public String customerName;

    /** Date the payment was made by the customer. */
    public Instant paymentDate;

    /** Date the funds were actually cleared in the bank. */
    public Instant receivedDate;

    /** Total amount of the payment. */
    public BigDecimal amount;

    /** ISO 4217 currency code, e.g. EUR. */
    public String currency;

    /** How the payment was made. */
    public PaymentMethod paymentMethod;

    /** Lifecycle status of this payment record. Defaults to PENDING. */
    public PaymentStatus status;

    /** Bank transaction reference number. */
    public String bankReference;

    /** Check number — relevant when paymentMethod is CHECK. */
    public String checkNumber;

    /** Free-text notes or internal comments. */
    public String notes;

    /** Invoice allocations — which invoices this payment covers, and how much. */
    public List<PaymentAllocation> allocations;

    /**
     * Amount not yet allocated to any invoice.
     * = amount - sum(allocations.allocatedAmount)
     * Positive: overpayment or partial application. Negative: over-allocated (should not happen).
     */
    public BigDecimal unallocatedAmount;

    /** Username of the operator who recorded this payment. */
    public String processedBy;

    /** Reason provided when the payment was reversed. */
    public String reversalReason;

    /**
     * If this record is a reversal entry, points to the original payment id being reversed.
     */
    public String reversedPaymentId;

    public Payment() {
        super();
        this.status = PaymentStatus.PENDING;
        this.allocations = new ArrayList<>();
    }
}
