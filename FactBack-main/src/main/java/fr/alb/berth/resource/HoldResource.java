package fr.alb.berth.resource;

import com.mongodb.client.model.Filters;
import fr.alb.berth.dao.HoldDao;
import fr.alb.berth.model.Hold;
import fr.alb.berth.model.Visit;
import fr.alb.dto.ErrorResponse;
import fr.alb.dto.HoldDTO;
import fr.alb.type.HoldType;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Visit-scoped operations on {@link Hold}s: list and create.
 *
 * <p>Visit existence is verified via a raw Mongo count (codec-free), so the
 * endpoint behaves correctly even on visits whose other fields are corrupted
 * (see TC-05.4).
 */
@Path("visit")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class HoldResource {

    @Inject
    HoldDao dao;

    @Inject
    SecurityIdentity identity;

    static String currentUserName(SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous() || identity.getPrincipal() == null) {
            return "system";
        }
        String name = identity.getPrincipal().getName();
        return (name == null || name.isBlank()) ? "system" : name;
    }

    static boolean visitExists(String visitId) {
        return Visit.mongoDatabase().getCollection("VESSEL_VISIT")
            .countDocuments(Filters.eq("_id", visitId)) > 0;
    }

    @GET
    @Path("/{visitId}/holds")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response list(@PathParam("visitId") String visitId) {
        try {
            UUID.fromString(visitId);
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "Malformed visit id", 400))
                .build();
        }
        if (!visitExists(visitId)) {
            return Response.status(404)
                .entity(new ErrorResponse("NOT_FOUND", "Visit not found: " + visitId, 404))
                .build();
        }
        List<HoldDTO> dtos = dao.findByVisitId(visitId).stream()
            .map(HoldDTO::from)
            .collect(Collectors.toList());
        return Response.ok(dtos).build();
    }

    @POST
    @Path("/{visitId}/holds")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Response create(@PathParam("visitId") String visitId, CreateHoldRequest req) {
        if (req == null) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "request body required", 400))
                .build();
        }
        if (req.type == null || req.type.isBlank() || !HoldType.isValid(req.type)) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "type is required and must be a known HoldType", 400))
                .build();
        }
        if (req.reason == null || req.reason.isBlank()) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "reason is required", 400))
                .build();
        }
        try {
            UUID.fromString(visitId);
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "Malformed visit id", 400))
                .build();
        }
        if (!visitExists(visitId)) {
            return Response.status(404)
                .entity(new ErrorResponse("NOT_FOUND", "Visit not found: " + visitId, 404))
                .build();
        }

        Hold hold = new Hold();
        hold.visitId = visitId;
        hold.type = HoldType.fromValue(req.type);
        hold.reason = req.reason.trim();
        hold.openedAt = Instant.now();
        hold.openedBy = currentUserName(identity);
        dao.add(hold);

        return Response.status(201).entity(HoldDTO.from(hold)).build();
    }

    public static class CreateHoldRequest {
        public String type;
        public String reason;
    }
}
