package fr.alb.yard.resource;

import java.util.List;
import java.util.stream.Collectors;

import fr.alb.yard.dao.EventConfigDao;
import fr.alb.dto.ErrorResponse;
import fr.alb.dto.EventDTO;
import fr.alb.dto.EventMapper;
import fr.alb.yard.model.EventConfig;
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

@Path("event")
public class EventConfigResource {

	@Inject
	EventConfigDao evtDao;
	
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
        public Response addEventConfig(EventConfig evt) {
                try {
                        evtDao.addEventConfig(evt);

                        return Response
                                        .status(201)
                                        .entity("Event created " + evt.getId())
                                        .build();
                } catch (Exception e) {
                        return Response.status(500)
                                .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                                .build();
                }
        }
	
	
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public List<EventDTO> getEventConfig(@QueryParam("q") String q, @QueryParam("scope") String scope) {
                fr.alb.type.EventScope scopeEnum = (scope == null || scope.isBlank()) ? null : fr.alb.type.EventScope.fromValue(scope);
                List<EventConfig> events = (scopeEnum == null)
                        ? evtDao.searchByName(q)
                        : evtDao.searchByNameAndScope(q, scopeEnum);
                return events.stream().map(EventMapper::toDTO).collect(Collectors.toList());
        }

        @GET
        @Path("{id}")
        @Produces(MediaType.APPLICATION_JSON)
        public Response getEventById(@PathParam("id") String id) {
                if (id == null || id.isBlank()) {
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(new ErrorResponse("BAD_REQUEST", "id is required", 400))
                                .build();
                }
                try {
                        java.util.UUID.fromString(id);
                } catch (IllegalArgumentException e) {
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(new ErrorResponse("BAD_REQUEST", "Malformed id", 400))
                                .build();
                }
                EventConfig evt = evtDao.findById(id);
                if (evt == null) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }
                EventDTO dto = EventMapper.toDTO(evt);
                return Response.ok(dto).build();
        }

        @PUT
        @Path("{id}")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        @RolesAllowed("ROLE_ADMIN")
        public Response updateEventConfig(@PathParam("id") String id, EventConfig payload) {
                try {
                        java.util.UUID.fromString(id);
                } catch (IllegalArgumentException e) {
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(new ErrorResponse("BAD_REQUEST", "Malformed id", 400))
                                .build();
                }
                EventConfig existing = evtDao.findById(id);
                if (existing == null) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }
                existing.setEventName(payload.getEventName());
                existing.setEventType(payload.getEventType());
                existing.setBilledEvent(payload.isBilledEvent());
                if (payload.getScope() != null) {
                        existing.setScope(payload.getScope());
                }
                evtDao.update(existing);
                return Response.ok(EventMapper.toDTO(existing)).build();
        }

        @DELETE
        @Path("{id}")
        @RolesAllowed("ROLE_ADMIN")
        public Response deleteEventConfig(@PathParam("id") String id) {
                try {
                        java.util.UUID.fromString(id);
                } catch (IllegalArgumentException e) {
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(new ErrorResponse("BAD_REQUEST", "Malformed id", 400))
                                .build();
                }
                if (evtDao.findById(id) == null) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }
                boolean removed = evtDao.deleteById(id);
                if (!removed) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response.noContent().build();
        }
}
