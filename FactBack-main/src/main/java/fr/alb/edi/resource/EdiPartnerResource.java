package fr.alb.edi.resource;

import java.util.List;

import fr.alb.dto.ErrorResponse;
import fr.alb.edi.model.EdiPartner;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * CRUD resource for EDI trading partners.
 *
 * All operations are restricted to administrators and EDI operators.
 */
@Path("edi/partners")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class EdiPartnerResource {

    /**
     * List all EDI partners (active and inactive).
     */
    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI"})
    public Response listPartners() {
        try {
            List<EdiPartner> partners = EdiPartner.listAll();
            return Response.ok(partners).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Retrieve a single EDI partner by its MongoDB ID.
     *
     * @param id the MongoDB string ID
     */
    @GET
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI"})
    public Response getPartner(@PathParam("id") String id) {
        EdiPartner partner = EdiPartner.findById(id);
        if (partner == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "EDI partner not found: " + id, 404))
                    .build();
        }
        return Response.ok(partner).build();
    }

    /**
     * Create a new EDI partner.
     *
     * @param partner the partner data; {@code partnerId} and {@code partnerName} are required
     */
    @POST
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI"})
    public Response createPartner(EdiPartner partner) {
        if (partner == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Request body is required", 400))
                    .build();
        }
        if (partner.getPartnerId() == null || partner.getPartnerId().isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "partnerId is required", 400))
                    .build();
        }
        if (partner.getPartnerName() == null || partner.getPartnerName().isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "partnerName is required", 400))
                    .build();
        }
        try {
            partner.persist();
            return Response.status(201).entity(partner).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Update an existing EDI partner.
     *
     * The entire partner document is replaced with the supplied body.
     *
     * @param id      the MongoDB string ID of the partner to update
     * @param updated the new partner data
     */
    @PUT
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI"})
    public Response updatePartner(@PathParam("id") String id, EdiPartner updated) {
        EdiPartner existing = EdiPartner.findById(id);
        if (existing == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "EDI partner not found: " + id, 404))
                    .build();
        }
        if (updated == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Request body is required", 400))
                    .build();
        }
        try {
            // Preserve the document ID
            updated.id = existing.id;
            updated.update();
            return Response.ok(updated).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Delete an EDI partner by its MongoDB ID.
     *
     * @param id the MongoDB string ID
     */
    @DELETE
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI"})
    public Response deletePartner(@PathParam("id") String id) {
        EdiPartner existing = EdiPartner.findById(id);
        if (existing == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "EDI partner not found: " + id, 404))
                    .build();
        }
        try {
            existing.delete();
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }
}
