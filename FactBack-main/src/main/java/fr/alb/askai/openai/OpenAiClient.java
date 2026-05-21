package fr.alb.askai.openai;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "openai")
@RegisterProvider(OpenAiClientExceptionMapper.class)
@ClientHeaderParam(name = "Authorization", value = "Bearer {apiKey}")
@ClientHeaderParam(name = "Accept", value = "application/json")
@CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.5, delay = 30, delayUnit = ChronoUnit.SECONDS)
public interface OpenAiClient {

    @POST
    @Path("/v1/responses")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(value = 30, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 1, delayUnit = ChronoUnit.SECONDS,
            retryOn = {java.io.IOException.class, jakarta.ws.rs.ProcessingException.class})
    OpenAiResponse create(OpenAiRequest request);

    @GET
    @Path("/v1/models")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> models();

    // --- headers ---
    default String apiKey() {
        String key = ConfigProvider.getConfig()
                .getOptionalValue("openai.api.key", String.class)
                .orElse("")
                .trim();
        if (key.isEmpty()) {
            throw new IllegalStateException(
                    "Missing OpenAI API key. Set 'openai.api.key' in application.properties or define OPENAI_API_KEY.");
        }
        return key;
    }

    // --- startup check (optional but helpful) ---
    @PostConstruct
    default void init() {
        Logger log = Logger.getLogger(OpenAiClient.class);

        String key = ConfigProvider.getConfig()
                .getOptionalValue("openai.api.key", String.class)
                .orElse("");
        if (key.isBlank()) {
            throw new IllegalStateException("Missing required configuration 'openai.api.key'.");
        }

        try {
            models(); // quick ping; if 401/403 we’ll remap to a clear error
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 401 || status == 403) {
                throw new IllegalStateException("Provided 'openai.api.key' was rejected by OpenAI", e);
            }
            log.warn("Unexpected OpenAI API error during init", e);
        } catch (RuntimeException e) {
            log.warn("Failed to verify OpenAI models endpoint", e);
        }
    }

    // --- DTOs for /v1/responses ---
    class OpenAiRequest {
        public String model;
        public String instructions;
        public List<Message> input;
        public Map<String, Object> text; // e.g. { "format": { "type": "json_object" } }

        public static class Message {
            public String role;          // "system" | "user" | "assistant"
            public List<Content> content;
        }

        public static class Content {
            public String type;          // "input_text"
            public String text;
        }
    }

    public class OpenAiResponse {
        public String id;
        public String status;                 // e.g., "completed"
        public String output_text;            // sometimes present
        public java.util.List<OutputItem> output; // primary place for text in Responses API

        // kept for backwards-compat (rarely present in Responses API)
        public java.util.List<Choice> choices;

        public String firstText() {
            // 1) Prefer the Responses API convenience field when present
            if (output_text != null && !output_text.isBlank()) return output_text;

            // 2) Parse the canonical Responses API structure
            if (output != null) {
                for (OutputItem item : output) {
                    if (item == null || item.content == null) continue;
                    for (OutputContent c : item.content) {
                        if (c != null && "output_text".equals(c.type) && c.text != null && !c.text.isBlank()) {
                            return c.text;
                        }
                    }
                }
            }

            // 3) Fallback: legacy Chat-like shape (rare under /v1/responses)
            if (choices != null && !choices.isEmpty()) {
                Choice ch = choices.get(0);
                if (ch != null && ch.message != null && ch.message.content != null && !ch.message.content.isEmpty()) {
                    Content ct = ch.message.content.get(0);
                    if (ct != null && ct.text != null && !ct.text.isBlank()) return ct.text;
                }
            }

            return null;
        }

        // --- nested shapes for Responses API ---
        public static class OutputItem {
            public java.util.List<OutputContent> content;
            public String id;                  // optional
            public String type;                // optional
        }

        public static class OutputContent {
            public String type;                // e.g., "output_text"
            public String text;                // the actual text
            // other content variants are possible (tool calls, etc.)
        }

        // --- legacy compatibility (rarely used by /v1/responses) ---
        public static class Choice { public Message message; }
        public static class Message { public java.util.List<Content> content; }
        public static class Content { public String text; }
    }

}
