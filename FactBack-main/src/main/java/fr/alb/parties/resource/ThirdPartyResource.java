package fr.alb.parties.resource;

import java.time.Instant;
import java.util.List;

import fr.alb.model.HistoryEntry;
import fr.alb.parties.model.ThirdParty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.bson.Document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@Path("third-party")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ThirdPartyResource {

    @Inject
    ObjectMapper mapper;

    @Inject
    fr.alb.parties.service.ThirdPartyService thirdPartyService;

    @POST
    @RolesAllowed("ROLE_ADMIN")
    public Response create(ThirdParty tp) throws JsonProcessingException {
        tp.persist();
        HistoryEntry h = new HistoryEntry();
        h.thirdPartyId = tp.getId();
        h.version = tp.version;
        h.updatedAt = tp.updatedAt;
        h.data = Document.parse(mapper.writeValueAsString(tp));
        h.persist();
        return Response.status(Response.Status.CREATED).entity(tp).build();
    }

    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public List<ThirdParty> list() {
        return ThirdParty.listAll();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public ThirdParty get(@PathParam("id") String id) {
        return ThirdParty.findById(id);
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("ROLE_ADMIN")
    public Response update(@PathParam("id") String id, ThirdParty tp) throws JsonProcessingException {
        ThirdParty current = ThirdParty.findById(id);
        if(current == null) {
            throw new NotFoundException();
        }
        HistoryEntry h = new HistoryEntry();
        h.thirdPartyId = current.getId();
        h.version = current.version;
        h.updatedAt = current.updatedAt;
        h.data = Document.parse(mapper.writeValueAsString(current));
        h.persist();

        // Use ThirdPartyService to handle version increment and timestamp update
        thirdPartyService.update(tp, current);
        tp.update();
        return Response.ok(tp).build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ROLE_ADMIN")
    public void delete(@PathParam("id") String id) {
        ThirdParty.deleteById(id);
    }

    @GET
    @Path("/{id}/history")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public List<HistoryEntry> getHistory(@PathParam("id") String id) {
        return HistoryEntry.find("thirdPartyId = ?1 order by version", id).list();
    }
}
