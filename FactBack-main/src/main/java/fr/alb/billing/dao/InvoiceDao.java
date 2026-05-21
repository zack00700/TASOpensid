package fr.alb.billing.dao;

import java.time.LocalDate;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import fr.alb.billing.model.Invoice;
import java.util.Optional;

public interface InvoiceDao {

       public Optional<Invoice> makeInvoice(List<String> itemsId, String customer, String billOfLadingId);

       /**
        * Retrieve every invoice stored in the system.
        *
        * @return list containing all invoices
        */
       public List<Invoice> getInvoices();

       public void finalizeInvoice(String invoiceId) throws Exception;

       /**
        * Generate an invoice for all items linked to the provided Bill of Lading.
        * The calculation respects contract rules and returns the computed amount.
        *
        * @param billOfLadingId identifier of the Bill of Lading whose items must be invoiced
        * @param invoiceDate    date on which the invoice is generated
        * @return total amount invoiced for all eligible items
        */
       public java.math.BigDecimal generateInvoiceByBillOfLading(String billOfLadingId, LocalDate invoiceDate);

       /**
        * Retrieve invoices linked to a specific vessel within the given date range.
        * The method joins invoice and shipping information (vessel visits) and
        * returns the combined data as BSON documents.
        *
        * @param vesselName name of the vessel (exact match)
        * @param startDate  inclusive start date filter
        * @param endDate    inclusive end date filter
        * @return list of documents containing invoice and shipping information
        */
       public List<Document> getInvoicesByVessel(String vesselName, LocalDate startDate, LocalDate endDate);

       /**
        * Query invoices with optional filters, pagination and sorting.
        * Returns a document containing an "items" array and a "meta" array
        * with totalCount and totalAmount.
        */
       public Document queryInvoices(int page, int pageSize, Bson sort,
                       List<String> statuses, String customerKey, String facilityKey,
                       String draftNumber, String finalNumber,
                       java.util.Date createdDateFrom, java.util.Date createdDateTo);

        /**
         * For each Document in `items` (a page of invoices serialized as Mongo Documents),
         * computes the sum of `Payment.allocations[].allocatedAmount` matching its `_id`,
         * and the most recent `Payment.paymentDate`. Mutates each Document in place by
         * adding `paidAmount: Number` and `lastPaymentDate: ISO-string | null`.
         * Returns the same list for fluency. A single Mongo query is issued for the page.
         */
        java.util.List<org.bson.Document> enrichWithPaymentSummary(java.util.List<org.bson.Document> items);
}
