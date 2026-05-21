package fr.alb.billing.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Embedded document — not a Panache entity.
 * Represents the portion of a Payment applied to a specific Invoice.
 */
public class PaymentAllocation {

    /** Stable identifier for this allocation entry. */
    public String allocationId;

    /** Reference to the Invoice being covered. */
    public String invoiceId;

    /** Denormalized invoice number for display without extra lookups. */
    public String invoiceNumber;

    /** Amount of this payment applied to the referenced invoice. */
    public BigDecimal allocatedAmount;

    /** When the allocation was recorded. */
    public Instant allocationDate;

    public PaymentAllocation() {
        this.allocationId = UUID.randomUUID().toString();
        this.allocationDate = Instant.now();
    }

    public String getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(String allocationId) {
        this.allocationId = allocationId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public void setAllocatedAmount(BigDecimal allocatedAmount) {
        this.allocatedAmount = allocatedAmount;
    }

    public Instant getAllocationDate() {
        return allocationDate;
    }

    public void setAllocationDate(Instant allocationDate) {
        this.allocationDate = allocationDate;
    }
}
