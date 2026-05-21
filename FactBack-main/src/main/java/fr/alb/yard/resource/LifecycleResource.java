package fr.alb.yard.resource;

import java.time.Instant;

import fr.alb.yard.model.Lifecycle;
import fr.alb.type.LifeCycleStatus;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("lifecycle")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LifecycleResource {

    public static class CancelRequest {
        public Instant endTime;
    }

    @POST
    @Path("/{id}/cancel")
    public Response cancel(@PathParam("id") String id, CancelRequest req) {
        Lifecycle lc = Lifecycle.findById(id);
        if (lc == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (lc.getStatus() != LifeCycleStatus.IN_PROGRESS) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        lc.setStatus(LifeCycleStatus.CANCELLED);
        lc.setEndTime(req != null && req.endTime != null ? req.endTime : Instant.now());
        lc.update();
        return Response.ok().build();
    }
}
