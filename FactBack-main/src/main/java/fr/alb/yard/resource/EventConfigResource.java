package fr.alb.yard.resource;

import java.util.List;
import java.util.stream.Collectors;

import fr.alb.yard.dao.EventConfigDao;
import fr.alb.dto.ErrorResponse;
import fr.alb.dto.EventDTO;
import fr.alb.dto.EventMapper;
import fr.alb.yard.model.EventConfig;
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
}
