package fr.alb.parties.resource;

import java.time.Instant;
import java.util.Objects;

import fr.alb.parties.model.ThirdParty;
import fr.alb.model.HistoryEntry;
import org.bson.Document;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;

@Path("third-party")
@RequestScoped
public class ThirdPartyPatchResource {

    @Inject
    ObjectMapper mapper;

    @Inject
    fr.alb.parties.service.ThirdPartyService thirdPartyService;

    @PATCH
    @Path("/{id}")
    @Consumes("application/json-patch+json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response patch(@PathParam("id") String id, String patchJson) throws Exception {
        ThirdParty current = ThirdParty.findById(id);
        if(current == null) {
            throw new NotFoundException();
        }
        JsonPatch patch = JsonPatch.fromJson(mapper.readTree(patchJson));
        JsonNode patchedNode = patch.apply(mapper.valueToTree(current));
        ThirdParty patched = mapper.treeToValue(patchedNode, ThirdParty.class);
        if(!Objects.equals(patched.version, current.version)) {
            throw new jakarta.persistence.OptimisticLockException("Version mismatch");
        }
        HistoryEntry h = new HistoryEntry();
        h.thirdPartyId = current.getId();
        h.version = current.version;
        h.updatedAt = current.updatedAt;
        h.data = Document.parse(mapper.writeValueAsString(current));
        h.persist();

        // Use ThirdPartyService to handle version increment and timestamp update
        thirdPartyService.update(patched, current);
        patched.update();
        return Response.ok(patched).build();
    }
}
