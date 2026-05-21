package fr.alb.dd.resource;

import java.util.List;

import fr.alb.dd.DdAccrualService;
import fr.alb.dto.ErrorResponse;
import fr.alb.dd.model.DdAccrual;
import fr.alb.dd.model.DdRule;
import fr.alb.dd.model.DdWaiver;
import fr.alb.type.DdAccrualStatus;
import fr.alb.type.DdType;
import io.quarkus.panache.common.Page;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for Demurrage & Detention accruals.
 *
 * Provides paginated listing, individual retrieval, waiver application, and
 * manual recomputation of accrual records.
 */
@Path("dd/accruals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class DdAccrualResource {

    @Inject
    DdAccrualService accrualService;

    /**
     * List accruals with optional filters: itemId, status, ddType.
     * Supports pagination via page/size query parameters.
     */
    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI", "ROLE_READONLY"})
    public Response listAccruals(
            @QueryParam("itemId") String itemId,
            @QueryParam("status") String status,
            @QueryParam("ddType") String ddType,
            @QueryParam("page") int page,
            @QueryParam("size") int size) {
        try {
            int p = Math.max(page, 1);
            int s = (size < 1 || size > 200) ? 50 : size;

            List<DdAccrual> accruals;

            boolean hasItem = itemId != null && !itemId.isBlank();
            boolean hasStatus = status != null && !status.isBlank();
            boolean hasType = ddType != null && !ddType.isBlank();

            if (hasItem && hasStatus && hasType) {
                DdAccrualStatus st = DdAccrualStatus.valueOf(status.toUpperCase());
                DdType type = DdType.valueOf(ddType.toUpperCase());
                accruals = DdAccrual.find(
                        "itemId = ?1 and status = ?2 and ddType = ?3", itemId, st, type)
                        .page(Page.of(p - 1, s)).list();
            } else if (hasItem && hasStatus) {
                DdAccrualStatus st = DdAccrualStatus.valueOf(status.toUpperCase());
                accruals = DdAccrual.find("itemId = ?1 and status = ?2", itemId, st)
                        .page(Page.of(p - 1, s)).list();
            } else if (hasItem && hasType) {
                DdType type = DdType.valueOf(ddType.toUpperCase());
                accruals = DdAccrual.find("itemId = ?1 and ddType = ?2", itemId, type)
                        .page(Page.of(p - 1, s)).list();
            } else if (hasStatus && hasType) {
                DdAccrualStatus st = DdAccrualStatus.valueOf(status.toUpperCase());
                DdType type = DdType.valueOf(ddType.toUpperCase());
                accruals = DdAccrual.find("status = ?1 and ddType = ?2", st, type)
                        .page(Page.of(p - 1, s)).list();
            } else if (hasItem) {
                accruals = DdAccrual.find("itemId", itemId)
                        .page(Page.of(p - 1, s)).list();
            } else if (hasStatus) {
                DdAccrualStatus st = DdAccrualStatus.valueOf(status.toUpperCase());
                accruals = DdAccrual.find("status", st)
                        .page(Page.of(p - 1, s)).list();
            } else if (hasType) {
                DdType type = DdType.valueOf(ddType.toUpperCase());
                accruals = DdAccrual.find("ddType", type)
                        .page(Page.of(p - 1, s)).list();
            } else {
                accruals = DdAccrual.find("{}").page(Page.of(p - 1, s)).list();
            }

            return Response.ok(accruals).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", e.getMessage(), 400))
                    .build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Get a single accrual by ID, including full dailyLog.
     */
    @GET
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI", "ROLE_READONLY"})
    public Response getAccrual(@PathParam("id") String id) {
        DdAccrual accrual = DdAccrual.findById(id);
        if (accrual == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "DdAccrual not found: " + id, 404))
                    .build();
        }
        return Response.ok(accrual).build();
    }

    /**
     * Apply a waiver to an accrual.
     * Delegates to DdAccrualService.applyWaiver() which handles status transitions
     * and recomputes totals.
     */
    @POST
    @Path("{id}/waiver")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI"})
    public Response applyWaiver(@PathParam("id") String id, DdWaiver waiver) {
        if (waiver == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Waiver body is required", 400))
                    .build();
        }
        try {
            DdAccrual updated = accrualService.applyWaiver(id, waiver);
            return Response.ok(updated).build();
        } catch (IllegalArgumentException e) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", e.getMessage(), 404))
                    .build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Manually trigger a recomputation of a single accrual.
     * Loads the associated DdRule and delegates to DdAccrualService.computeAndUpdate().
     */
    @POST
    @Path("{id}/recompute")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI"})
    public Response recompute(@PathParam("id") String id) {
        DdAccrual accrual = DdAccrual.findById(id);
        if (accrual == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "DdAccrual not found: " + id, 404))
                    .build();
        }
        DdRule rule = DdRule.findById(accrual.ruleId);
        if (rule == null) {
            return Response.status(422)
                    .entity(new ErrorResponse("UNPROCESSABLE", "DdRule not found for accrual: " + accrual.ruleId, 422))
                    .build();
        }
        try {
            accrualService.computeAndUpdate(accrual, rule);
            DdAccrual refreshed = DdAccrual.findById(id);
            return Response.ok(refreshed).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }
}
