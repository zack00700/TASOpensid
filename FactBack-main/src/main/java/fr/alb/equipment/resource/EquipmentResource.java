package fr.alb.equipment.resource;

import fr.alb.equipment.model.Crane;
import fr.alb.equipment.model.Operator;
import fr.alb.equipment.model.Shift;
import fr.alb.equipment.model.YardMachine;
import fr.alb.equipment.service.EquipmentStatusService;
import fr.alb.equipment.service.ShiftService;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST facade for the equipment bounded context.
 *
 * <ul>
 *     <li>{@code /equipment/cranes}   — QC, RTG, RMG, MHC fleet.</li>
 *     <li>{@code /equipment/machines} — non-crane yard equipment.</li>
 *     <li>{@code /equipment/operators} — staff certified to operate.</li>
 *     <li>{@code /equipment/shifts}   — operator × time window, with
 *         start / end / cancel transitions.</li>
 * </ul>
 */
@Path("/equipment")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EquipmentResource {

    @Inject
    ShiftService shiftService;

    @Inject
    EquipmentStatusService statusService;

    // ── Cranes ──────────────────────────────────────────────────────────────

    @GET
    @Path("cranes")
    public List<Crane> listCranes(@QueryParam("type") Crane.CraneType type,
                                  @QueryParam("status") Crane.Status status) {
        if (type != null && status != null) return Crane.list("type = ?1 and status = ?2", type, status);
        if (type != null) return Crane.list("type", type);
        if (status != null) return Crane.list("status", status);
        return Crane.listAll();
    }

    @POST
    @Path("cranes")
    @RolesAllowed("ROLE_ADMIN")
    public Response createCrane(Crane crane) {
        if (crane == null || crane.code == null || crane.type == null) {
            throw new BadRequestException("code and type are required");
        }
        crane.persist();
        return Response.status(Response.Status.CREATED).entity(crane).build();
    }

    @POST
    @Path("cranes/{id}/status")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Crane setCraneStatus(@PathParam("id") String id, Map<String, String> body) {
        if (body == null || body.get("status") == null) throw new BadRequestException("status is required");
        return statusService.updateCraneStatus(id,
                Crane.Status.valueOf(body.get("status").toUpperCase()),
                body.get("moveId"));
    }

    // ── Yard machines ───────────────────────────────────────────────────────

    @GET
    @Path("machines")
    public List<YardMachine> listMachines(@QueryParam("type") YardMachine.MachineType type,
                                          @QueryParam("status") YardMachine.Status status) {
        if (type != null && status != null) return YardMachine.list("type = ?1 and status = ?2", type, status);
        if (type != null) return YardMachine.list("type", type);
        if (status != null) return YardMachine.list("status", status);
        return YardMachine.listAll();
    }

    @POST
    @Path("machines")
    @RolesAllowed("ROLE_ADMIN")
    public Response createMachine(YardMachine machine) {
        if (machine == null || machine.code == null || machine.type == null) {
            throw new BadRequestException("code and type are required");
        }
        machine.persist();
        return Response.status(Response.Status.CREATED).entity(machine).build();
    }

    @POST
    @Path("machines/{id}/status")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public YardMachine setMachineStatus(@PathParam("id") String id, Map<String, String> body) {
        if (body == null || body.get("status") == null) throw new BadRequestException("status is required");
        return statusService.updateMachineStatus(id,
                YardMachine.Status.valueOf(body.get("status").toUpperCase()),
                body.get("moveId"));
    }

    // ── Operators ───────────────────────────────────────────────────────────

    @GET
    @Path("operators")
    public List<Operator> listOperators(@QueryParam("active") Boolean activeOnly) {
        if (Boolean.TRUE.equals(activeOnly)) return Operator.list("active", true);
        return Operator.listAll();
    }

    @POST
    @Path("operators")
    @RolesAllowed("ROLE_ADMIN")
    public Response createOperator(Operator operator) {
        if (operator == null || operator.employeeCode == null
                || operator.firstName == null || operator.lastName == null) {
            throw new BadRequestException("employeeCode, firstName and lastName are required");
        }
        operator.persist();
        return Response.status(Response.Status.CREATED).entity(operator).build();
    }

    @PUT
    @Path("operators/{id}")
    @RolesAllowed("ROLE_ADMIN")
    public Operator updateOperator(@PathParam("id") String id, Operator updated) {
        Operator existing = Operator.findById(id);
        if (existing == null) throw new NotFoundException("Operator " + id + " not found");
        existing.employeeCode = updated.employeeCode;
        existing.firstName = updated.firstName;
        existing.lastName = updated.lastName;
        existing.userId = updated.userId;
        existing.certifications = updated.certifications;
        existing.lastTrainingDate = updated.lastTrainingDate;
        existing.active = updated.active;
        existing.update();
        return existing;
    }

    // ── Shifts ──────────────────────────────────────────────────────────────

    @GET
    @Path("shifts")
    public List<Shift> listShifts(@QueryParam("operatorId") String operatorId,
                                  @QueryParam("status") Shift.Status status) {
        if (operatorId != null && status != null) {
            return Shift.list("operatorId = ?1 and status = ?2", operatorId, status);
        }
        if (operatorId != null) return Shift.list("operatorId", operatorId);
        if (status != null) return Shift.list("status", status);
        return Shift.list("order by scheduledStart desc");
    }

    @POST
    @Path("shifts")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Response scheduleShift(Shift shift) {
        Shift created = shiftService.schedule(shift);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @POST
    @Path("shifts/{id}/start")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Shift startShift(@PathParam("id") String id, Map<String, Object> body) {
        Instant when = body != null && body.get("actualStart") != null
                ? Instant.parse(body.get("actualStart").toString()) : null;
        return shiftService.start(id, when);
    }

    @POST
    @Path("shifts/{id}/end")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Shift endShift(@PathParam("id") String id, Map<String, Object> body) {
        Instant when = body != null && body.get("actualEnd") != null
                ? Instant.parse(body.get("actualEnd").toString()) : null;
        return shiftService.end(id, when);
    }

    @POST
    @Path("shifts/{id}/cancel")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Shift cancelShift(@PathParam("id") String id, Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return shiftService.cancel(id, reason);
    }
}
