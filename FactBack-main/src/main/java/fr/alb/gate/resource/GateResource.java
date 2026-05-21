package fr.alb.gate.resource;

import fr.alb.gate.model.GateEvent;
import fr.alb.gate.model.GatePass;
import fr.alb.gate.model.TruckAppointment;
import fr.alb.gate.service.GateService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * REST facade for the gate bounded context.
 *
 * <ul>
 *     <li>{@code /gate/appointments} — pre-announce, reject, cancel, list.</li>
 *     <li>{@code /gate/passes}       — issue and revoke gate passes.</li>
 *     <li>{@code /gate/events}       — record IN / OUT scans, audit log.</li>
 * </ul>
 */
@Path("/gate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GateResource {

    @Inject
    GateService gateService;

    // ── Appointments ────────────────────────────────────────────────────────

    @POST
    @Path("appointments")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Response createAppointment(TruckAppointment in) {
        TruckAppointment created = gateService.createAppointment(in);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("appointments")
    public List<TruckAppointment> listAppointments(
            @QueryParam("status") TruckAppointment.Status status,
            @QueryParam("licensePlate") String licensePlate) {
        if (status != null && licensePlate != null) {
            return TruckAppointment.list("status = ?1 and licensePlate = ?2", status, licensePlate);
        }
        if (status != null) return TruckAppointment.list("status", status);
        if (licensePlate != null) return TruckAppointment.list("licensePlate", licensePlate);
        return TruckAppointment.listAll();
    }

    @GET
    @Path("appointments/{id}")
    public TruckAppointment getAppointment(@PathParam("id") String id) {
        TruckAppointment ap = TruckAppointment.findById(id);
        if (ap == null) throw new NotFoundException("Appointment " + id + " not found");
        return ap;
    }

    @POST
    @Path("appointments/{id}/reject")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public TruckAppointment rejectAppointment(@PathParam("id") String id, Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return gateService.rejectAppointment(id, reason);
    }

    @POST
    @Path("appointments/{id}/cancel")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public TruckAppointment cancelAppointment(@PathParam("id") String id, Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return gateService.cancelAppointment(id, reason);
    }

    // ── Gate passes ─────────────────────────────────────────────────────────

    @POST
    @Path("appointments/{id}/issue-pass")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Response issuePass(@PathParam("id") String appointmentId, Map<String, String> body) {
        String issuedBy = body != null ? body.get("issuedBy") : null;
        GatePass pass = gateService.issuePass(appointmentId, issuedBy);
        return Response.status(Response.Status.CREATED).entity(pass).build();
    }

    @GET
    @Path("passes/{id}")
    public GatePass getPass(@PathParam("id") String id) {
        GatePass pass = GatePass.findById(id);
        if (pass == null) throw new NotFoundException("Gate pass " + id + " not found");
        return pass;
    }

    @POST
    @Path("passes/{id}/revoke")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public GatePass revokePass(@PathParam("id") String id, Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return gateService.revokePass(id, reason);
    }

    // ── Gate events ─────────────────────────────────────────────────────────

    @POST
    @Path("events/in")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public GateEvent recordIn(Map<String, String> body) {
        if (body == null || body.get("code") == null) {
            throw new BadRequestException("code is required");
        }
        return gateService.recordGateIn(body.get("code"), body.get("gateCode"), body.get("recordedBy"));
    }

    @POST
    @Path("events/out")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public GateEvent recordOut(Map<String, String> body) {
        if (body == null || body.get("appointmentId") == null) {
            throw new BadRequestException("appointmentId is required");
        }
        return gateService.recordGateOut(body.get("appointmentId"),
                body.get("gateCode"), body.get("recordedBy"));
    }

    @GET
    @Path("events")
    public List<GateEvent> listEvents(@QueryParam("appointmentId") String appointmentId,
                                      @QueryParam("direction") GateEvent.Direction direction) {
        if (appointmentId != null && direction != null) {
            return GateEvent.list("appointmentId = ?1 and direction = ?2 order by occurredAt desc",
                    appointmentId, direction);
        }
        if (appointmentId != null) return GateEvent.list("appointmentId = ?1 order by occurredAt desc", appointmentId);
        if (direction != null)     return GateEvent.list("direction = ?1 order by occurredAt desc", direction);
        return GateEvent.list("order by occurredAt desc");
    }
}
