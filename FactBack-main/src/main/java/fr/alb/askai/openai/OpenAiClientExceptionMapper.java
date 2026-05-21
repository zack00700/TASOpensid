package fr.alb.askai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.logging.Logger;

@Provider
public class OpenAiClientExceptionMapper implements ResponseExceptionMapper<WebApplicationException> {
    private static final Logger LOG = Logger.getLogger(OpenAiClientExceptionMapper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public WebApplicationException toThrowable(Response response) {
        int status = response.getStatus();
        if (status == 401) {
            String message = "Invalid OpenAI API key";
            LOG.error(message);
            return new WebApplicationException(message, status);
        }

        String body = null;
        try { body = response.readEntity(String.class); } catch (Exception ignore) {}

        String message = "Unknown error";
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = MAPPER.readTree(body);
                JsonNode msg = root.path("error").path("message");
                if (msg.isTextual()) message = msg.asText();
                else message = body;
            } catch (Exception e) {
                message = body;
            }
        }

        LOG.errorf("Unexpected OpenAI API status %d: %s", status, body);
        return new WebApplicationException(message, status);
    }

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status >= 400;
    }
}
