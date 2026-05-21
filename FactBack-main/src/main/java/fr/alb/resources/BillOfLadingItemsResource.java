package fr.alb.resources;

import fr.alb.dto.items.ItemDiffPayload;
import fr.alb.yard.service.ItemDiffService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/billoflading/{blId}/items:apply-diff")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BillOfLadingItemsResource {

    @Inject
    ItemDiffService itemDiffService;

    @POST
    @Transactional
    public Response applyDiff(@PathParam("blId") String blId, ItemDiffPayload payload) {
        var result = itemDiffService.applyItemDiff(blId, payload);
        return Response.ok(result).build();
    }
}
