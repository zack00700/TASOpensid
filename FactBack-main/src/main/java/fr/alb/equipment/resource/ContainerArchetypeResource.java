package fr.alb.equipment.resource;

import fr.alb.equipment.model.ContainerArchetype;
import fr.alb.equipment.model.IsoContainerCode;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/archetypes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ContainerArchetypeResource {

    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response list() {
        List<ContainerArchetype> all = ContainerArchetype.<ContainerArchetype>findAll().list();
        all.sort((a, b) -> a.code == null ? 1 : b.code == null ? -1 : a.code.compareTo(b.code));
        return Response.ok(all).build();
    }

    @GET
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response get(@PathParam("id") String id) {
        ContainerArchetype a = ContainerArchetype.findById(id);
        if (a == null) return Response.status(404).build();
        return Response.ok(a).build();
    }

    @GET
    @Path("{id}/iso-codes")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response listIsoCodes(@PathParam("id") String id) {
        if (ContainerArchetype.findById(id) == null) return Response.status(404).build();
        List<IsoContainerCode> codes =
            IsoContainerCode.<IsoContainerCode>find("archetypeId", id).list();
        return Response.ok(codes).build();
    }

    @POST
    @Transactional
    @RolesAllowed("ROLE_ADMIN")
    public Response create(ContainerArchetype payload) {
        if (payload.code == null || payload.code.isBlank()) {
            return Response.status(400).entity(Map.of("error", "code_required")).build();
        }
        if (payload.name == null || payload.name.isBlank()) {
            return Response.status(400).entity(Map.of("error", "name_required")).build();
        }
        if (ContainerArchetype.find("code", payload.code).firstResult() != null) {
            return Response.status(409).entity(Map.of("error", "duplicate_code")).build();
        }
        payload.persist();
        return Response.status(201).entity(payload).build();
    }

    @PUT
    @Path("{id}")
    @Transactional
    @RolesAllowed("ROLE_ADMIN")
    public Response update(@PathParam("id") String id, ContainerArchetype payload) {
        ContainerArchetype existing = ContainerArchetype.findById(id);
        if (existing == null) return Response.status(404).build();

        if (payload.code != null && !payload.code.equals(existing.code)) {
            if (ContainerArchetype.find("code = ?1 and id != ?2", payload.code, id).firstResult() != null) {
                return Response.status(409).entity(Map.of("error", "duplicate_code")).build();
            }
            existing.code = payload.code;
        }
        if (payload.name != null) existing.name = payload.name;
        existing.description = payload.description;
        existing.isActive = payload.isActive;
        existing.update();
        return Response.ok(existing).build();
    }

    @DELETE
    @Path("{id}")
    @Transactional
    @RolesAllowed("ROLE_ADMIN")
    public Response delete(@PathParam("id") String id) {
        ContainerArchetype existing = ContainerArchetype.findById(id);
        if (existing == null) return Response.status(404).build();

        long inUse = IsoContainerCode.count("archetypeId", id);
        if (inUse > 0) {
            return Response.status(409)
                .entity(Map.of("error", "archetype_in_use", "count", inUse))
                .build();
        }
        existing.delete();
        return Response.noContent().build();
    }
}
