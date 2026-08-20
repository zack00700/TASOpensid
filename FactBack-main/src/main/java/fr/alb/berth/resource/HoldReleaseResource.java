package fr.alb.berth.resource;

import fr.alb.berth.dao.HoldDao;
import fr.alb.berth.model.Hold;
import fr.alb.dto.ErrorResponse;
import fr.alb.dto.HoldDTO;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.UUID;

/**
 * Hold-scoped action: release.
 *
 * <p>Separate from {@link HoldResource} only because JAX-RS expects a class-level
 * {@code @Path}, and the two paths live at different roots ({@code /visit/...}
 * vs {@code /holds/...}).
 */
@Path("holds")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class HoldReleaseResource {

    @Inject
    HoldDao dao;

    @Inject
    SecurityIdentity identity;

    @PATCH
    @Path("/{holdId}/release")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Response release(@PathParam("holdId") String holdId, ReleaseHoldRequest req) {
        try {
            UUID.fromString(holdId);
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "Malformed hold id", 400))
                .build();
        }
        Hold hold = dao.findById(holdId);
        if (hold == null) {
            return Response.status(404)
                .entity(new ErrorResponse("NOT_FOUND", "Hold not found: " + holdId, 404))
                .build();
        }
        if (!hold.isActive()) {
            return Response.status(409)
                .entity(new ErrorResponse("ALREADY_RELEASED",
                    "Hold was already released at " + hold.releasedAt, 409))
                .build();
        }

        hold.releasedAt = Instant.now();
        hold.releasedBy = HoldResource.currentUserName(identity);
        hold.releaseNotes = (req != null && req.releaseNotes != null) ? req.releaseNotes.trim() : null;
        dao.update(hold);

        return Response.ok(HoldDTO.from(hold)).build();
    }

    public static class ReleaseHoldRequest {
        public String releaseNotes;
    }
}
