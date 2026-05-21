package fr.alb.customs.resource;

import fr.alb.customs.api.CustomsClearanceChecker;
import fr.alb.customs.model.CustomsDeclaration;
import fr.alb.customs.service.CustomsDeclarationService;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST façade for the customs bounded context.
 *
 * <ul>
 *     <li>{@code GET /customs/declarations} — list (filter by status / BOL / item).</li>
 *     <li>{@code POST /customs/declarations} — create a DRAFT.</li>
 *     <li>{@code POST /customs/declarations/{id}/submit} — hand to customs.</li>
 *     <li>{@code POST /customs/declarations/{id}/hold} — customs puts on hold.</li>
 *     <li>{@code POST /customs/declarations/{id}/clear} — customs clears, optional {@code assessedDuties}.</li>
 *     <li>{@code POST /customs/declarations/{id}/reject} — customs rejects with reason.</li>
 *     <li>{@code GET /customs/items/{itemId}/status} — quick clearance check for UIs and gate.</li>
 * </ul>
 */
@Path("/customs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomsResource {

    @Inject
    CustomsDeclarationService service;

    @Inject
    CustomsClearanceChecker checker;

    @GET
    @Path("declarations")
    public List<CustomsDeclaration> list(@QueryParam("status") CustomsDeclaration.Status status,
                                         @QueryParam("billOfLadingId") String bolId,
                                         @QueryParam("itemId") String itemId) {
        if (status != null && bolId != null) {
            return CustomsDeclaration.list("status = ?1 and billOfLadingId = ?2", status, bolId);
        }
        if (itemId != null) return CustomsDeclaration.list("itemIds", itemId);
        if (bolId != null)  return CustomsDeclaration.list("billOfLadingId", bolId);
        if (status != null) return CustomsDeclaration.list("status", status);
        return CustomsDeclaration.findAll(Sort.by("submittedAt", Sort.Direction.Descending)).list();
    }

    @GET
    @Path("declarations/{id}")
    public CustomsDeclaration get(@PathParam("id") String id) {
        CustomsDeclaration d = CustomsDeclaration.findById(id);
        if (d == null) throw new NotFoundException("Declaration " + id + " not found");
        return d;
    }

    @POST
    @Path("declarations")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Response create(CustomsDeclaration declaration) {
        CustomsDeclaration created = service.create(declaration);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @POST
    @Path("declarations/{id}/submit")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public CustomsDeclaration submit(@PathParam("id") String id) {
        return service.submit(id);
    }

    @POST
    @Path("declarations/{id}/hold")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public CustomsDeclaration hold(@PathParam("id") String id, Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        if (reason == null || reason.isBlank()) throw new BadRequestException("reason is required");
        return service.hold(id, reason);
    }

    @POST
    @Path("declarations/{id}/clear")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public CustomsDeclaration clear(@PathParam("id") String id, Map<String, Object> body) {
        BigDecimal duties = null;
        if (body != null && body.get("assessedDuties") != null) {
            duties = new BigDecimal(body.get("assessedDuties").toString());
        }
        return service.clear(id, duties);
    }

    @POST
    @Path("declarations/{id}/reject")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public CustomsDeclaration reject(@PathParam("id") String id, Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        if (reason == null || reason.isBlank()) throw new BadRequestException("reason is required");
        return service.reject(id, reason);
    }

    /** Fast lookup used by the gate dashboard and the frontend badge. */
    @GET
    @Path("items/{itemId}/status")
    public Map<String, Object> itemStatus(@PathParam("itemId") String itemId) {
        boolean cleared = checker.isCleared(itemId);
        return Map.of(
                "itemId", itemId,
                "cleared", cleared,
                "blockReason", cleared ? "" : String.valueOf(checker.lastBlockReason(itemId)));
    }
}
