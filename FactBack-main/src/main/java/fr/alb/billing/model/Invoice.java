package fr.alb.billing.model;

import fr.alb.model.EntityBase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.quarkus.mongodb.panache.common.MongoEntity;
import fr.alb.billing.model.RateManagement.TaxBreakdownItem;

@MongoEntity(collection = "INVOICE")
public class Invoice extends EntityBase {

	private static final long serialVersionUID = 1L;


    public String draftNumber;
	public String finalNumber;
        public String status;
        public String customerName;
        // Uppercased/trimmed versions used for prefix searches
        public String customerKey;
        public double amount;
        public String facility;
        public String facilityKey;
        public LocalDate createdDate;
        public List<InvoiceLineSnap> lines;
        public String currency;
        public String billOfLadingId;
        public BigDecimal subtotalAmount;
        public BigDecimal inclusiveTaxTotal;
        public BigDecimal exclusiveTaxTotal;
        public BigDecimal totalTaxAmount;
        public BigDecimal grandTotalAmount;
        public List<TaxBreakdownItem> taxBreakdown;
        public List<String> taxCalculationIds;
        /**
         * Optional reference to a {@link InvoiceTemplate} to use when rendering this
         * invoice.
         */
        public String templateId;
        public List<String> itemIds;

        /**
         * Idempotency key: prevents creating duplicate draft invoices.
         * Format: SHA-256 of sorted(itemIds) + customerName.
         * Indexed for fast lookup.
         */
        public String idempotencyKey;

        // === Missing fields for production invoicing ===

        /** Official invoice issue date (distinct from createdDate which is system timestamp). */
        private LocalDate invoiceDate;

        /** Payment due date — required for aging/receivables analysis. */
        private LocalDate dueDate;

        /** Payment terms code, e.g. "NET30", "2/10 NET30", "COD". */
        private String paymentTerms;

        /** Start of the service period covered by this invoice. */
        private LocalDate invoicePeriodStart;

        /** End of the service period covered by this invoice. */
        private LocalDate invoicePeriodEnd;

        /** Customer purchase order reference number. */
        private String poNumber;

        /** Payment status: UNPAID, PARTIALLY_PAID, PAID, OVERDUE, DISPUTED. */
        private String paymentStatus; // default "UNPAID"

        /** Outstanding balance (grand total minus payments applied). */
        private BigDecimal balanceDue;

        /** Reference to the Contract governing this invoice's rates. */
        private String billingContractId;

        /** User who cancelled the invoice. */
        private String cancelledBy;

        /** When the invoice was cancelled. */
        private Instant cancelledDate;

        /** Reason for cancellation. */
        private String cancellationReason;

        /** User who approved the invoice. */
        private String approvedBy;

        /** When the invoice was approved. */
        private Instant approvalDate;

        /** When the invoice was sent to the customer. */
        private Instant sentDate;

        /** How the invoice was delivered: EMAIL, EDI, PORTAL, PAPER. */
        private String deliveryMethod;

        /** Internal notes (not visible to customer). */
        private String internalNotes;

        /** Customer-facing notes printed on the invoice. */
        private String customerNotes;


	public Invoice() {
		super();
		this.status = "DRAFT";
		this.paymentStatus = "UNPAID";
		this.balanceDue = BigDecimal.ZERO;
	}

        public LocalDate getInvoiceDate() { return invoiceDate; }
        public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

        public LocalDate getDueDate() { return dueDate; }
        public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

        public String getPaymentTerms() { return paymentTerms; }
        public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

        public LocalDate getInvoicePeriodStart() { return invoicePeriodStart; }
        public void setInvoicePeriodStart(LocalDate invoicePeriodStart) { this.invoicePeriodStart = invoicePeriodStart; }

        public LocalDate getInvoicePeriodEnd() { return invoicePeriodEnd; }
        public void setInvoicePeriodEnd(LocalDate invoicePeriodEnd) { this.invoicePeriodEnd = invoicePeriodEnd; }

        public String getPoNumber() { return poNumber; }
        public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

        public BigDecimal getBalanceDue() { return balanceDue; }
        public void setBalanceDue(BigDecimal balanceDue) { this.balanceDue = balanceDue; }

        public String getBillingContractId() { return billingContractId; }
        public void setBillingContractId(String billingContractId) { this.billingContractId = billingContractId; }

        public String getCancelledBy() { return cancelledBy; }
        public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }

        public Instant getCancelledDate() { return cancelledDate; }
        public void setCancelledDate(Instant cancelledDate) { this.cancelledDate = cancelledDate; }

        public String getCancellationReason() { return cancellationReason; }
        public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

        public Instant getApprovalDate() { return approvalDate; }
        public void setApprovalDate(Instant approvalDate) { this.approvalDate = approvalDate; }

        public Instant getSentDate() { return sentDate; }
        public void setSentDate(Instant sentDate) { this.sentDate = sentDate; }

        public String getDeliveryMethod() { return deliveryMethod; }
        public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }

        public String getInternalNotes() { return internalNotes; }
        public void setInternalNotes(String internalNotes) { this.internalNotes = internalNotes; }

        public String getCustomerNotes() { return customerNotes; }
        public void setCustomerNotes(String customerNotes) { this.customerNotes = customerNotes; }
}
