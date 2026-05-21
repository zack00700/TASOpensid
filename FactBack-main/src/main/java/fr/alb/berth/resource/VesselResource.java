package fr.alb.berth.resource;

import fr.alb.berth.model.Vessel;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/vessel")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VesselResource {

    /**
     * Lists all vessels.
     * Read-only operation - no transaction needed as it only queries data.
     */
    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response list() {
        return Response.ok(Vessel.listAll()).build();
    }

    /**
     * Gets a single vessel by ID.
     * Read-only operation - no transaction needed as it only queries data.
     */
    @GET
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response get(@PathParam("id") String id) {
        Vessel vessel = Vessel.findById(id);
        if (vessel == null) {
            return Response.status(404).build();
        }
        return Response.ok(vessel).build();
    }

    @POST
    @Path("/bulk")
    @Transactional
    @RolesAllowed("ROLE_ADMIN")
    //@Transactional
    public Response createBulk(List<Vessel> vessels) {
        Vessel.persist(vessels);
        return Response.status(201).entity(vessels).build();
    }

    @POST
    @Transactional
    @RolesAllowed("ROLE_ADMIN")

    public Response push(Vessel vessel) {
        vessel.persist();
        return Response.status(201).entity(vessel).build();
    }

    @PUT
    @Transactional
    @RolesAllowed("ROLE_ADMIN")

    public Response update(Vessel vessel) {
        Vessel current = Vessel.findById(vessel.getId());
        if (current == null) {
            return Response.status(404).build();
        }
        vessel.setId(current.getId());
        vessel.update();
        return Response.ok(vessel).build();
    }

    @DELETE
    @Path("{id}")
    @Transactional
    @RolesAllowed("ROLE_ADMIN")

    public Response delete(@PathParam("id") String id) {
        boolean deleted = Vessel.deleteById(id);
        if (deleted) {
            return Response.noContent().build();
        }
        return Response.status(404).build();
    }
}

