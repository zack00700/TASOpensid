package fr.alb.api;

import fr.alb.billing.domain.ChargeRecord;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * Read-only API for charge audit records.
 * Exposes the full calculation trail (inputs, explanation, calculator used)
 * for dispute resolution and invoice transparency.
 */
@Path("/charge-records")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ROLE_ADMIN", "ROLE_BILLING_USER", "ROLE_INVOICE_ADMIN", "ROLE_READONLY"})
public class ChargeRecordResource {

    @GET
    @Path("/invoice/{invoiceId}")
    public List<ChargeRecord> byInvoice(@PathParam("invoiceId") String invoiceId) {
        return ChargeRecord.findByInvoice(invoiceId);
    }

    @GET
    @Path("/item/{itemId}")
    public List<ChargeRecord> byItem(@PathParam("itemId") String itemId) {
        return ChargeRecord.findByItem(itemId);
    }
}
