package fr.alb.dd.resource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.alb.dto.ErrorResponse;
import fr.alb.dd.model.DdAccrual;
import fr.alb.type.DdAccrualStatus;
import fr.alb.type.DdType;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for the Demurrage & Detention operational dashboard.
 *
 * Provides aggregated summary metrics for the operations team.
 */
@Path("dd/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class DdDashboardResource {

    /**
     * Return a summary of running, waived, and invoiced D&D accruals together
     * with the total monetary exposure for all currently RUNNING accruals.
     *
     * Response shape:
     * {
     *   "runningDemurrage": &lt;long&gt;,
     *   "runningDetention": &lt;long&gt;,
     *   "totalExposure":    &lt;BigDecimal&gt;,
     *   "overdueCount":     &lt;long&gt;,
     *   "waivedCount":      &lt;long&gt;,
     *   "invoicedCount":    &lt;long&gt;
     * }
     */
    @GET
    @Path("summary")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI", "ROLE_READONLY"})
    public Response getSummary() {
        try {
            long runningDemurrage = DdAccrual.count(
                    "status = ?1 and ddType = ?2", DdAccrualStatus.RUNNING, DdType.DEMURRAGE);

            long runningDetention = DdAccrual.count(
                    "status = ?1 and ddType = ?2", DdAccrualStatus.RUNNING, DdType.DETENTION);

            // Sum totalAccruedAmount for all RUNNING accruals
            List<DdAccrual> runningAccruals = DdAccrual.find("status", DdAccrualStatus.RUNNING).list();
            BigDecimal totalExposure = runningAccruals.stream()
                    .map(a -> a.totalAccruedAmount != null ? a.totalAccruedAmount : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Overdue: RUNNING accruals that have started accumulating chargeable days
            long overdueCount = runningAccruals.stream()
                    .filter(a -> a.chargeableDays > 0)
                    .count();

            long waivedCount = DdAccrual.count("status", DdAccrualStatus.WAIVED);
            long invoicedCount = DdAccrual.count("status", DdAccrualStatus.INVOICED);

            Map<String, Object> summary = new HashMap<>();
            summary.put("runningDemurrage", runningDemurrage);
            summary.put("runningDetention", runningDetention);
            summary.put("totalExposure", totalExposure);
            summary.put("overdueCount", overdueCount);
            summary.put("waivedCount", waivedCount);
            summary.put("invoicedCount", invoicedCount);

            return Response.ok(summary).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }
}
