package fr.alb.billing.resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import fr.alb.billing.model.Invoice;
import fr.alb.billing.util.PaymentTermsParser;
import fr.alb.parties.model.ThirdParty;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

/**
 * One-shot admin endpoint to backfill Invoice.paymentTerms and Invoice.dueDate
 * for legacy records created before those fields were populated automatically.
 * Idempotent: filters on `dueDate is null`, so re-runs only touch newly-discovered nulls.
 */
@Path("/admin/invoices")
@RunOnVirtualThread
public class AdminInvoiceBackfillResource {

    private static final Logger LOG = Logger.getLogger(AdminInvoiceBackfillResource.class);

    @POST
    @Path("backfill-due-dates")
    @RolesAllowed({"ROLE_ADMIN"})
    public Response backfillDueDates() {
        long updated = 0;
        long skipped = 0;
        List<Invoice> targets = Invoice.<Invoice>find("{ 'dueDate': null }").list();
        LOG.infof("backfill-due-dates: found %d invoices with null dueDate", targets.size());

        for (Invoice inv : targets) {
            if (inv.createdDate == null) {
                LOG.warnf("backfill-due-dates: skipping invoice id=%s (no createdDate)", inv.id);
                skipped++;
                continue;
            }
            String tpTerms = null;
            if (inv.customerKey != null && !inv.customerKey.isEmpty()) {
                ThirdParty tp = ThirdParty.find("customerCode = ?1 or _id = ?1", inv.customerKey).firstResult();
                if (tp != null) tpTerms = tp.getPaymentTermsDefault();
            }
            int days = PaymentTermsParser.parseDays(tpTerms);
            inv.setPaymentTerms("NET" + days);
            inv.setDueDate(inv.createdDate.plusDays(days));
            inv.update();
            updated++;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("found", targets.size());
        body.put("updated", updated);
        body.put("skipped", skipped);
        LOG.infof("backfill-due-dates: updated=%d skipped=%d", updated, skipped);
        return Response.ok(body).build();
    }
}
