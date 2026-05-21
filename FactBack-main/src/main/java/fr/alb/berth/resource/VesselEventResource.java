package fr.alb.berth.resource;

import fr.alb.berth.dao.VesselEventDao;
import fr.alb.berth.model.Visit;
import fr.alb.berth.model.VesselEvent;
import fr.alb.dto.ErrorResponse;
import fr.alb.type.EventScope;
import fr.alb.yard.api.EventConfigApi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Path("visit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VesselEventResource {

    @Inject
    VesselEventDao dao;

    @Inject
    EventConfigApi eventConfigApi;

    @POST
    @Path("/{visitId}/event")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Response createEvent(@PathParam("visitId") String visitId, VesselEventRequest req) {
        if (req == null) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "request body required", 400)).build();
        }
        if (req.eventId == null || req.eventId.isBlank()) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "eventId is required", 400)).build();
        }
        if (req.notes == null || req.notes.isBlank()) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "notes is required", 400)).build();
        }
        if (req.notes.length() > 500) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "notes max 500 chars", 400)).build();
        }
        if (req.eventDate == null) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "eventDate is required", 400)).build();
        }

        Visit visit = Visit.findById(visitId);
        if (visit == null) {
            return Response.status(404)
                .entity(new ErrorResponse("NOT_FOUND", "Visit not found: " + visitId, 404)).build();
        }

        if (!eventConfigApi.exists(req.eventId)) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST", "Unknown eventId", 400)).build();
        }
        EventScope scope = eventConfigApi.getScope(req.eventId);
        if (scope != EventScope.VESSEL && scope != EventScope.BOTH) {
            return Response.status(400)
                .entity(new ErrorResponse("BAD_REQUEST",
                    "EventConfig scope must be VESSEL or BOTH, got " + scope, 400)).build();
        }

        VesselEvent event = new VesselEvent();
        event.visitId = visitId;
        event.eventId = req.eventId;
        event.eventDate = req.eventDate;
        event.notes = req.notes;

        dao.addVesselEvent(event);

        return Response.status(201)
            .entity(Map.of("id", event.getId(), "message", "Vessel event recorded"))
            .build();
    }

    @GET
    @Path("/{visitId}/event")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public List<VesselEvent> listEvents(@PathParam("visitId") String visitId) {
        return dao.findByVisitId(visitId);
    }

    public static class VesselEventRequest {
        public String eventId;
        public Instant eventDate;
        public String notes;
    }
}
