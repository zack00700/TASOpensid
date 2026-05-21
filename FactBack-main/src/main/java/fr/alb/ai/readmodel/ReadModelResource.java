package fr.alb.ai.readmodel;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * REST entry point for the CQRS read model. Dashboards and Ask AI hit
 * these endpoints instead of aggregating against write-side collections.
 *
 * <p>All responses are projections — they are eventually consistent with
 * the write side (latency = the time it takes a CDI observer to run). Callers
 * that need transactional read-your-writes guarantees must still go through
 * the write-context resource.
 */
@Path("/readmodel")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
public class ReadModelResource {

    @GET
    @Path("invoices")
    public List<InvoiceDigest> listInvoiceDigests(@QueryParam("customerId") String customerId,
                                                  @QueryParam("yearMonth") String yearMonth,
                                                  @QueryParam("currency") String currency) {
        if (customerId != null && yearMonth != null) {
            return InvoiceDigest.list("customerId = ?1 and yearMonth = ?2", customerId, yearMonth);
        }
        if (customerId != null) return InvoiceDigest.list("customerId", customerId);
        if (yearMonth != null) return InvoiceDigest.list("yearMonth", yearMonth);
        if (currency != null) return InvoiceDigest.list("currency", currency);
        return InvoiceDigest.list("order by finalizedAt desc");
    }

    @GET
    @Path("invoices/monthly-revenue")
    public List<MonthlyRevenueBucket> listMonthlyRevenue(@QueryParam("currency") String currency,
                                                         @QueryParam("from") String fromYearMonth,
                                                         @QueryParam("to") String toYearMonth) {
        if (currency != null && fromYearMonth != null && toYearMonth != null) {
            return MonthlyRevenueBucket.list(
                    "currency = ?1 and yearMonth >= ?2 and yearMonth <= ?3 order by yearMonth",
                    currency, fromYearMonth, toYearMonth);
        }
        if (fromYearMonth != null && toYearMonth != null) {
            return MonthlyRevenueBucket.list(
                    "yearMonth >= ?1 and yearMonth <= ?2 order by yearMonth",
                    fromYearMonth, toYearMonth);
        }
        if (currency != null) {
            return MonthlyRevenueBucket.list("currency = ?1 order by yearMonth", currency);
        }
        return MonthlyRevenueBucket.list("order by yearMonth desc");
    }
}
