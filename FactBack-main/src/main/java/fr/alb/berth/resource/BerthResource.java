package fr.alb.berth.resource;

import fr.alb.berth.dto.AllocationRequest;
import fr.alb.berth.model.BerthAllocation;
import fr.alb.berth.model.BerthSlot;
import fr.alb.berth.service.BerthAllocationService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST facade for the berth bounded context.
 *
 * <p>Two resource families under a single {@code /berths} root:
 * <ul>
 *     <li>{@code /berths} — master data for berth slots (read mostly, create
 *         for admins).</li>
 *     <li>{@code /berths/allocations} — the planning surface: create, list,
 *         mark berthed/departed, cancel.</li>
 * </ul>
 */
@Path("/berths")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BerthResource {

    @Inject
    BerthAllocationService allocationService;

    // ── Berth slots ─────────────────────────────────────────────────────────

    @GET
    public List<BerthSlot> listSlots(@QueryParam("active") Boolean activeOnly) {
        if (Boolean.TRUE.equals(activeOnly)) {
            return BerthSlot.list("active", true);
        }
        return BerthSlot.listAll();
    }

    @POST
    @RolesAllowed("ROLE_ADMIN")
    public Response createSlot(BerthSlot slot) {
        if (slot == null || slot.name == null || slot.name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        slot.persist();
        return Response.status(Response.Status.CREATED).entity(slot).build();
    }

    @PUT
    @Path("{id}")
    @RolesAllowed("ROLE_ADMIN")
    public BerthSlot updateSlot(@PathParam("id") String id, BerthSlot updated) {
        BerthSlot existing = BerthSlot.findById(id);
        if (existing == null) throw new NotFoundException("Berth slot " + id + " not found");
        existing.name = updated.name;
        existing.code = updated.code;
        existing.lengthMeters = updated.lengthMeters;
        existing.depthMeters = updated.depthMeters;
        existing.maxDraftMeters = updated.maxDraftMeters;
        existing.description = updated.description;
        existing.active = updated.active;
        existing.update();
        return existing;
    }

    // ── Allocations ─────────────────────────────────────────────────────────

    @POST
    @Path("allocations")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Response createAllocation(AllocationRequest req) {
        BerthAllocation allocation = allocationService.allocate(
                req.berthSlotId, req.vesselVisitId, req.vesselName,
                req.scheduledArrival, req.scheduledDeparture, req.notes);
        return Response.status(Response.Status.CREATED).entity(allocation).build();
    }

    @GET
    @Path("allocations")
    public List<BerthAllocation> listAllocations(@QueryParam("berthSlotId") String berthSlotId,
                                                 @QueryParam("status") BerthAllocation.Status status,
                                                 @QueryParam("from") Instant from,
                                                 @QueryParam("to") Instant to) {
        StringBuilder q = new StringBuilder("1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        int idx = 1;
        if (berthSlotId != null) { q.append(" and berthSlotId = ?").append(idx++); args.add(berthSlotId); }
        if (status != null) { q.append(" and status = ?").append(idx++); args.add(status); }
        if (from != null) { q.append(" and scheduledDeparture > ?").append(idx++); args.add(from); }
        if (to != null) { q.append(" and scheduledArrival < ?").append(idx++); args.add(to); }
        return BerthAllocation.list(q.toString(), args.toArray());
    }

    @GET
    @Path("allocations/{id}")
    public BerthAllocation getAllocation(@PathParam("id") String id) {
        BerthAllocation allocation = BerthAllocation.findById(id);
        if (allocation == null) throw new NotFoundException("Allocation " + id + " not found");
        return allocation;
    }

    @POST
    @Path("allocations/{id}/berthed")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public BerthAllocation markBerthed(@PathParam("id") String id, Map<String, Object> body) {
        Instant when = body != null && body.get("actualBerthed") != null
                ? Instant.parse(body.get("actualBerthed").toString())
                : null;
        return allocationService.markBerthed(id, when);
    }

    @POST
    @Path("allocations/{id}/departed")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public BerthAllocation markDeparted(@PathParam("id") String id, Map<String, Object> body) {
        Instant when = body != null && body.get("actualDeparted") != null
                ? Instant.parse(body.get("actualDeparted").toString())
                : null;
        return allocationService.markDeparted(id, when);
    }

    @DELETE
    @Path("allocations/{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public BerthAllocation cancelAllocation(@PathParam("id") String id, @QueryParam("reason") String reason) {
        return allocationService.cancel(id, reason);
    }
}
