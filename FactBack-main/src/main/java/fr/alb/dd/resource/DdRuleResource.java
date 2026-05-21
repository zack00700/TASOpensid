package fr.alb.dd.resource;

import java.util.List;
import java.util.Optional;

import fr.alb.dto.ErrorResponse;
import fr.alb.dd.model.DdRule;
import fr.alb.equipment.api.IsoContainerCodeRegistry;
import fr.alb.equipment.validation.ContainerTypeValidator;
import fr.alb.type.DdType;
import io.quarkus.panache.common.Page;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for Demurrage & Detention rules.
 *
 * Manages DdRule documents that define free-day allowances and rate tiers
 * for a given carrier / container type combination.
 */
@Path("dd/rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class DdRuleResource {

    @Inject
    IsoContainerCodeRegistry isoContainerCodeRegistry;

    /**
     * List all D&D rules, optionally filtered by ddType.
     */
    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI", "ROLE_READONLY"})
    public Response listRules(@QueryParam("ddType") String ddType) {
        try {
            List<DdRule> rules;
            if (ddType != null && !ddType.isBlank()) {
                DdType type = DdType.valueOf(ddType.toUpperCase());
                rules = DdRule.find("ddType", type).list();
            } else {
                rules = DdRule.find("{}").list();
            }
            return Response.ok(rules).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Invalid ddType: " + ddType, 400))
                    .build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Get a single D&D rule by ID.
     */
    @GET
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI", "ROLE_READONLY"})
    public Response getRule(@PathParam("id") String id) {
        DdRule rule = DdRule.findById(id);
        if (rule == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "DdRule not found: " + id, 404))
                    .build();
        }
        return Response.ok(rule).build();
    }

    /**
     * Create a new D&D rule.
     * Required fields: ruleName, ddType, clockAnchor.
     */
    @POST
    @RolesAllowed({"ROLE_ADMIN"})
    public Response createRule(DdRule rule) {
        if (rule == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Request body is required", 400))
                    .build();
        }
        if (rule.ruleName == null || rule.ruleName.isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "ruleName is required", 400))
                    .build();
        }
        if (rule.ddType == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "ddType is required", 400))
                    .build();
        }
        if (rule.clockAnchor == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "clockAnchor is required", 400))
                    .build();
        }
        Optional<Response> ctValidation = ContainerTypeValidator.validate(
                rule.containerTypeCode,
                isoContainerCodeRegistry::contains);
        if (ctValidation.isPresent()) return ctValidation.get();
        try {
            rule.persist();
            return Response.status(201).entity(rule).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Update an existing D&D rule.
     * Loads the existing document, overwrites all writable fields, and persists.
     */
    @PUT
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN"})
    public Response updateRule(@PathParam("id") String id, DdRule incoming) {
        DdRule existing = DdRule.findById(id);
        if (existing == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "DdRule not found: " + id, 404))
                    .build();
        }
        if (incoming == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Request body is required", 400))
                    .build();
        }
        Optional<Response> ctValidation = ContainerTypeValidator.validate(
                incoming.containerTypeCode,
                isoContainerCodeRegistry::contains);
        if (ctValidation.isPresent()) return ctValidation.get();
        try {
            existing.ruleName = incoming.ruleName;
            existing.ddType = incoming.ddType;
            existing.clockAnchor = incoming.clockAnchor;
            existing.carrierId = incoming.carrierId;
            existing.containerTypeCode = incoming.containerTypeCode;
            existing.freeDays = incoming.freeDays;
            existing.tiers = incoming.tiers;
            existing.includeHolidays = incoming.includeHolidays;
            existing.includeWeekends = incoming.includeWeekends;
            existing.status = incoming.status;
            existing.notes = incoming.notes;
            existing.update();
            return Response.ok(existing).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Delete a D&D rule by ID.
     */
    @DELETE
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN"})
    public Response deleteRule(@PathParam("id") String id) {
        try {
            boolean deleted = DdRule.delete("_id", id) > 0;
            if (!deleted) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "DdRule not found: " + id, 404))
                        .build();
            }
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }
}
