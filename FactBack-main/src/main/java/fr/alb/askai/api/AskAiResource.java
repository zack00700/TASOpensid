package fr.alb.askai.api;

import fr.alb.askai.model.AskAiRequest;
import fr.alb.askai.model.AskAiSpec;
import fr.alb.askai.service.AskAiService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.UUID;

@Path("/ask-ai")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AskAiResource {

    private static final Logger LOG = Logger.getLogger(AskAiResource.class);

    @Inject
    AskAiService service;

    @POST
    public AskAiSpec ask(@Valid AskAiRequest request) {
        String reqId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        LOG.infof("ask-ai %s start", reqId);
        try {
            AskAiSpec spec = service.handle(request.getQuestion());
            long duration = System.currentTimeMillis() - start;
            LOG.infof("ask-ai %s done in %dms", reqId, duration);
            return spec;
        } catch (WebApplicationException e) {
            LOG.errorf("ask-ai %s error: %s", reqId, e.getMessage());
            throw e;
        }
    }
}
