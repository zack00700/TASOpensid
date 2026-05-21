package fr.alb.common;

import fr.alb.dto.ErrorResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@ApplicationScoped
public class OptimisticLockExceptionMapper implements ExceptionMapper<OptimisticLockException> {

    @Override
    public Response toResponse(OptimisticLockException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse("CONFLICT", "Optimistic lock failure", 409))
                .build();
    }
}
