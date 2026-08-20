package fr.alb.berth.resource;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import fr.alb.berth.dao.HoldDao;
import fr.alb.berth.model.Visit;
import fr.alb.dto.ErrorResponse;
import fr.alb.type.VisitPhase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.bson.Document;

import java.util.Map;
import java.util.UUID;

/**
 * Phase-transition endpoint for {@link Visit}. Mounted under the same
 * {@code /visit} path as the auto-generated CRUD resource — it adds a
 * {@code PATCH /{id}/phase} subroute that the auto-generated interface
 * does not expose.
 *
 * <p>Reads and writes happen at the raw Mongo level via
 * {@code Visit.mongoDatabase().getCollection(...)} rather than through
 * Panache's POJO codec. Some legacy visits have date fields stored as
 * strings rather than BSON {@code DATE_TIME}, which crashes any code
 * path that triggers full deserialization (see TC-05.4). Keeping this
 * endpoint codec-free lets it operate even on corrupted documents.
 */
@Path("visit")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VisitPhaseResource {

    @Inject
    HoldDao holdDao;

    @PATCH
    @Path("/{id}/phase")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Response updatePhase(@PathParam("id") String id, PhaseChangeRequest req) {
        if (req == null || req.phase == null || req.phase.isBlank()) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "phase is required", 400))
                .build();
        }
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "Malformed id", 400))
                .build();
        }
        if (!VisitPhase.isValid(req.phase)) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "Unknown phase: " + req.phase, 400))
                .build();
        }

        MongoCollection<Document> coll = Visit.mongoDatabase().getCollection("VESSEL_VISIT");
        Document existing = coll
            .find(Filters.eq("_id", id))
            .projection(new Document("phase", 1))
            .first();
        if (existing == null) {
            return Response.status(404)
                .entity(new ErrorResponse("NOT_FOUND", "Visit not found: " + id, 404))
                .build();
        }

        VisitPhase target = VisitPhase.fromValue(req.phase);
        String currentRaw = existing.getString("phase");
        // Legacy / missing / unrecognized current phase is treated as Created, so a
        // visit without phase metadata can still be advanced to Active.
        VisitPhase current = (currentRaw != null && VisitPhase.isValid(currentRaw))
            ? VisitPhase.fromValue(currentRaw)
            : VisitPhase.CREATED;

        if (current == target) {
            return Response.status(409)
                .entity(new ErrorResponse(
                    "INVALID_TRANSITION",
                    "Visit is already in phase " + target.getValue(),
                    409))
                .build();
        }
        if (!current.allowedNextPhases().contains(target)) {
            return Response.status(409)
                .entity(new ErrorResponse(
                    "INVALID_TRANSITION",
                    "Cannot transition from " + current.getValue() + " to " + target.getValue(),
                    409))
                .build();
        }

        // Forward moves (Created->Active, Active->Completed) are blocked by any
        // open hold. Cancellation is always allowed — operators cancel because
        // of holds, not in spite of them.
        if (target != VisitPhase.CANCELED && holdDao.countActiveByVisitId(id) > 0) {
            return Response.status(409)
                .entity(new ErrorResponse(
                    "HOLDS_ACTIVE",
                    "Cannot advance visit while one or more holds are active",
                    409))
                .build();
        }

        coll.updateOne(Filters.eq("_id", id), Updates.set("phase", target.getValue()));

        return Response.ok(Map.of("id", id, "phase", target.getValue())).build();
    }

    public static class PhaseChangeRequest {
        public String phase;
    }
}
