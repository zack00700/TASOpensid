package fr.alb.common;

import com.github.fge.jsonpatch.JsonPatchException;
import fr.alb.dto.ErrorResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@ApplicationScoped
public class JsonPatchExceptionMapper implements ExceptionMapper<JsonPatchException> {
    @Override
    public Response toResponse(JsonPatchException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("BAD_REQUEST", exception.getMessage(), 400))
                .build();
    }
}
