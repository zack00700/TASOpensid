package fr.alb.askai.api;

import fr.alb.askai.model.AskAiRequest;
import fr.alb.askai.model.AskAiSpec;
import fr.alb.askai.service.AskAiAvailability;
import fr.alb.askai.service.AskAiService;
import fr.alb.dto.ErrorResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;

@Path("/ask-ai")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AskAiResource {

    private static final Logger LOG = Logger.getLogger(AskAiResource.class);

    @Inject
    AskAiService service;

    @Inject
    AskAiAvailability availability;

    @POST
    public Response ask(@Valid AskAiRequest request) {
        if (!availability.isEnabled()) {
            LOG.warn("ask-ai called but feature is disabled (no API key configured)");
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ErrorResponse(
                            "AI_NOT_CONFIGURED",
                            "AI assistant is not configured on this environment.",
                            503))
                    .build();
        }

        String reqId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        LOG.infof("ask-ai %s start", reqId);
        try {
            AskAiSpec spec = service.handle(request.getQuestion());
            long duration = System.currentTimeMillis() - start;
            LOG.infof("ask-ai %s done in %dms", reqId, duration);
            return Response.ok(spec).build();
        } catch (WebApplicationException e) {
            LOG.errorf("ask-ai %s error: %s", reqId, e.getMessage());
            throw e;
        }
    }

    /**
     * Lightweight status endpoint so the UI can hide the Ask AI entry point
     * when the backend has no LLM key.
     */
    @GET
    @Path("/status")
    public Map<String, Object> status() {
        return Map.of(
                "enabled", availability.isEnabled(),
                "providers", Map.of(
                        "openai", availability.hasOpenAi(),
                        "anthropic", availability.hasAnthropic()
                )
        );
    }
}
