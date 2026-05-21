package fr.alb.equipment.resource;

import fr.alb.equipment.model.ContainerArchetype;
import fr.alb.equipment.model.IsoContainerCode;
import fr.alb.equipment.validation.IsoCodeValidator;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/iso-codes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class IsoContainerCodeResource {

    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response list(@QueryParam("includeInactive") @DefaultValue("false") boolean includeInactive) {
        List<IsoContainerCode> codes = includeInactive
            ? IsoContainerCode.<IsoContainerCode>findAll().list()
            : IsoContainerCode.<IsoContainerCode>find("isActive", true).list();
        codes.sort((a, b) -> a.code == null ? 1 : b.code == null ? -1 : a.code.compareTo(b.code));
        return Response.ok(codes).build();
    }

    @GET
    @Path("{code}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response get(@PathParam("code") String code) {
        IsoContainerCode c = IsoContainerCode.<IsoContainerCode>find("code", code).firstResult();
        if (c == null) return Response.status(404).build();
        return Response.ok(c).build();
    }

    @POST
    @Transactional
    @RolesAllowed("ROLE_ADMIN")
    public Response create(IsoContainerCode payload) {
        if (!IsoCodeValidator.isValidCode(payload.code)) {
            return Response.status(400).entity(Map.of("error", "invalid_code")).build();
        }
        if (IsoContainerCode.find("code", payload.code).firstResult() != null) {
            return Response.status(409).entity(Map.of("error", "duplicate_code")).build();
        }
        if (payload.archetypeId != null && ContainerArchetype.findById(payload.archetypeId) == null) {
            return Response.status(400).entity(Map.of("error", "archetype_not_found")).build();
        }
        // Force isStandard=false for any operator-created code, regardless of payload.
        payload.isStandard = false;
        payload.persist();
        return Response.status(201).entity(payload).build();
    }

    /**
     * Full replace of the mutable fields (PUT semantics, not PATCH). Callers must echo
     * back the complete object — booleans not present in the payload will be persisted
     * as false, weights as null. Locked fields (code, isStandard, and on standard codes
     * also lengthFt/heightFt/typeGroup) are ignored regardless of payload.
     */
    @PUT
    @Path("{code}")
    @Transactional
    @RolesAllowed("ROLE_ADMIN")
    public Response update(@PathParam("code") String code, IsoContainerCode payload) {
        IsoContainerCode existing = IsoContainerCode.<IsoContainerCode>find("code", code).firstResult();
        if (existing == null) return Response.status(404).build();

        if (payload.archetypeId != null && ContainerArchetype.findById(payload.archetypeId) == null) {
            return Response.status(400).entity(Map.of("error", "archetype_not_found")).build();
        }

        // Always-locked fields: code, isStandard. Standard codes also lock physical dimensions.
        if (!existing.isStandard) {
            if (payload.lengthFt != null) existing.lengthFt = payload.lengthFt;
            if (payload.heightFt != null) existing.heightFt = payload.heightFt;
            if (payload.typeGroup != null) existing.typeGroup = payload.typeGroup;
        }

        if (payload.description != null) existing.description = payload.description;
        existing.isReefer = payload.isReefer;
        existing.isHazmatCapable = payload.isHazmatCapable;
        existing.isTank = payload.isTank;
        existing.isOpenTop = payload.isOpenTop;
        existing.isActive = payload.isActive;
        existing.archetypeId = payload.archetypeId;
        existing.tareKg = payload.tareKg;
        existing.maxPayloadKg = payload.maxPayloadKg;
        existing.maxGrossKg = payload.maxGrossKg;
        existing.update();
        return Response.ok(existing).build();
    }

    @DELETE
    @Path("{code}")
    @Transactional
    @RolesAllowed("ROLE_ADMIN")
    public Response delete(@PathParam("code") String code) {
        IsoContainerCode existing = IsoContainerCode.<IsoContainerCode>find("code", code).firstResult();
        if (existing == null) return Response.status(404).build();
        if (existing.isStandard) {
            return Response.status(409)
                .entity(Map.of("error", "cannot_delete_standard_code"))
                .build();
        }
        existing.delete();
        return Response.noContent().build();
    }
}
