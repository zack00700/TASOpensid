package fr.alb.edi.resource;

import java.util.List;

import fr.alb.dto.ErrorResponse;
import fr.alb.edi.EdiMessageMapper;
import fr.alb.edi.EdiProcessorService;
import fr.alb.edi.model.EdiMessage;
import fr.alb.billing.model.Invoice;
import io.quarkus.panache.common.Page;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * EDI integration endpoints.
 *
 * Provides inbound message ingestion, status tracking, and manual processing
 * triggers.  Outbound INVOIC generation is also exposed here.
 */
@Path("edi")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class EdiResource {

    @Inject
    EdiProcessorService processorService;

    @Inject
    EdiMessageMapper ediMessageMapper;

    /**
     * Ingest an inbound EDI message.
     * The message is stored as-is with status RECEIVED for async processing.
     */
    @POST
    @Path("inbound")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI"})
    public Response receiveMessage(EdiMessage message) {
        if (message == null || message.rawPayload == null || message.rawPayload.isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "rawPayload is required", 400))
                    .build();
        }
        try {
            message.direction = EdiMessage.Direction.INBOUND;
            message.status = EdiMessage.EdiStatus.RECEIVED;
            message.persist();
            return Response.status(201).entity(message).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * List recent EDI messages with optional status filter.
     */
    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI", "ROLE_READONLY"})
    public Response listMessages(
            @QueryParam("status") String status,
            @QueryParam("partnerId") String partnerId,
            @QueryParam("page") int page,
            @QueryParam("size") int size) {
        try {
            int p = Math.max(page, 1);
            int s = (size < 1 || size > 200) ? 50 : size;
            List<EdiMessage> messages;
            if (status != null && !status.isBlank()) {
                EdiMessage.EdiStatus ediStatus = EdiMessage.EdiStatus.valueOf(status.toUpperCase());
                messages = EdiMessage.find("status", ediStatus)
                        .page(Page.of(p - 1, s)).list();
            } else {
                messages = EdiMessage.find("{}").page(Page.of(p - 1, s)).list();
            }
            return Response.ok(messages).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Get a single EDI message by ID.
     */
    @GET
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI", "ROLE_READONLY"})
    public Response getMessage(@PathParam("id") String id) {
        EdiMessage msg = EdiMessage.findById(id);
        if (msg == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "EDI message not found: " + id, 404))
                    .build();
        }
        return Response.ok(msg).build();
    }

    /**
     * Trigger synchronous processing of a stored EDI message.
     *
     * The message must already exist in the DB (created via POST /edi/inbound).
     * Processing is idempotent: already-PROCESSED messages are silently skipped.
     */
    @POST
    @Path("{id}/process")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI"})
    public Response processMessage(@PathParam("id") String id) {
        EdiMessage msg = EdiMessage.findById(id);
        if (msg == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "EDI message not found: " + id, 404))
                    .build();
        }
        try {
            processorService.process(id);
            EdiMessage updated = EdiMessage.findById(id);
            return Response.ok(updated).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Generate an outbound INVOIC EDI payload for the given invoice ID.
     *
     * Returns the raw pipe-delimited text that can be forwarded to a trading
     * partner or stored as an outbound EdiMessage.
     */
    @GET
    @Path("outbound/invoic/{invoiceId}")
    @Produces(MediaType.TEXT_PLAIN)
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI", "ROLE_INVOICE_ADMIN"})
    public Response generateInvoic(@PathParam("invoiceId") String invoiceId) {
        Invoice invoice = Invoice.findById(invoiceId);
        if (invoice == null) {
            return Response.status(404)
                    .entity("Invoice not found: " + invoiceId)
                    .build();
        }
        try {
            String payload = ediMessageMapper.generateInvoic(invoice);
            return Response.ok(payload).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity("Error generating INVOIC: " + e.getMessage())
                    .build();
        }
    }
}
