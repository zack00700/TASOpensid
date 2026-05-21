package fr.alb.api;

import fr.alb.dto.ErrorResponse;
import fr.alb.billing.model.Invoice;
import fr.alb.billing.service.InvoiceComputationService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@RequestScoped
@Path("/invoice")
@Produces(MediaType.APPLICATION_JSON)
public class InvoiceDebugResource {

    @Inject InvoiceComputationService invoiceComputationService;

    @GET
    @Path("{id}/debug-lines")
    public Response debugLines(@PathParam("id") String id) {
        Invoice inv = Invoice.findById(id);
        if (inv == null) return Response.status(404).entity(new ErrorResponse("NOT_FOUND", "Invoice not found", 404)).build();
        var diags = invoiceComputationService.computeDiagnostics(inv);
        return Response.ok(diags).build();
    }
}
