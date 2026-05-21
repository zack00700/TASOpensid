package fr.alb.yard.resource;

import fr.alb.yard.dto.YardMoveRequest;
import fr.alb.yard.model.ContainerMove;
import fr.alb.yard.model.YardBlock;
import fr.alb.yard.model.YardSlot;
import fr.alb.yard.service.YardAllocationService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
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

import java.util.List;
import java.util.Map;

/**
 * REST facade for the yard bounded context.
 *
 * <ul>
 *     <li>{@code /yard/blocks} — master data for yard blocks.</li>
 *     <li>{@code /yard/slots}  — individual positions (bay-row-tier).</li>
 *     <li>{@code /yard/moves}  — the container movement log and move API.</li>
 * </ul>
 */
@Path("/yard")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class YardResource {

    @Inject
    YardAllocationService yardService;

    // ── Blocks ──────────────────────────────────────────────────────────────

    @GET
    @Path("blocks")
    public List<YardBlock> listBlocks(@QueryParam("active") Boolean activeOnly,
                                      @QueryParam("kind") YardBlock.BlockKind kind) {
        if (Boolean.TRUE.equals(activeOnly) && kind != null) {
            return YardBlock.list("active = true and kind = ?1", kind);
        }
        if (Boolean.TRUE.equals(activeOnly)) return YardBlock.list("active", true);
        if (kind != null) return YardBlock.list("kind", kind);
        return YardBlock.listAll();
    }

    @POST
    @Path("blocks")
    @RolesAllowed("ROLE_ADMIN")
    public Response createBlock(YardBlock block) {
        if (block == null || block.name == null || block.name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        block.persist();
        return Response.status(Response.Status.CREATED).entity(block).build();
    }

    @PUT
    @Path("blocks/{id}")
    @RolesAllowed("ROLE_ADMIN")
    public YardBlock updateBlock(@PathParam("id") String id, YardBlock updated) {
        YardBlock existing = YardBlock.findById(id);
        if (existing == null) throw new NotFoundException("Block " + id + " not found");
        existing.name = updated.name;
        existing.code = updated.code;
        existing.kind = updated.kind;
        existing.capacityTeu = updated.capacityTeu;
        existing.active = updated.active;
        existing.allowedCargo = updated.allowedCargo;
        existing.update();
        return existing;
    }

    // ── Slots ───────────────────────────────────────────────────────────────

    @GET
    @Path("slots")
    public List<YardSlot> listSlots(@QueryParam("blockId") String blockId,
                                    @QueryParam("free") Boolean freeOnly) {
        if (blockId != null && Boolean.TRUE.equals(freeOnly)) {
            return YardSlot.list("blockId = ?1 and currentItemId is null and active = true", blockId);
        }
        if (blockId != null) return YardSlot.list("blockId", blockId);
        if (Boolean.TRUE.equals(freeOnly)) {
            return YardSlot.list("currentItemId is null and active = true");
        }
        return YardSlot.listAll();
    }

    @POST
    @Path("slots")
    @RolesAllowed("ROLE_ADMIN")
    public Response createSlot(YardSlot slot) {
        if (slot == null || slot.blockId == null || slot.code == null) {
            throw new BadRequestException("blockId and code are required");
        }
        slot.persist();
        return Response.status(Response.Status.CREATED).entity(slot).build();
    }

    // ── Moves ───────────────────────────────────────────────────────────────

    @POST
    @Path("moves/allocate")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public YardSlot allocateForArrival(Map<String, String> body) {
        if (body == null || body.get("itemId") == null) {
            throw new BadRequestException("itemId is required");
        }
        return yardService.allocateForArrival(body.get("itemId"), body.get("preferredBlockId"));
    }

    @POST
    @Path("moves")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public ContainerMove move(YardMoveRequest req) {
        return yardService.move(req.itemId, req.toSlotId, req.reason, req.operator, req.notes);
    }

    @POST
    @Path("moves/release")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public ContainerMove release(Map<String, String> body) {
        if (body == null || body.get("itemId") == null) {
            throw new BadRequestException("itemId is required");
        }
        return yardService.release(body.get("itemId"), body.get("operator"), body.get("notes"));
    }

    @GET
    @Path("moves")
    public List<ContainerMove> listMoves(@QueryParam("itemId") String itemId,
                                         @QueryParam("reason") ContainerMove.MoveReason reason) {
        if (itemId != null && reason != null) {
            return ContainerMove.list("itemId = ?1 and reason = ?2 order by movedAt desc", itemId, reason);
        }
        if (itemId != null) return ContainerMove.list("itemId = ?1 order by movedAt desc", itemId);
        if (reason != null) return ContainerMove.list("reason = ?1 order by movedAt desc", reason);
        return ContainerMove.list("order by movedAt desc");
    }
}
