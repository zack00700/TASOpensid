package fr.alb.common;

import fr.alb.dto.ErrorResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Gestionnaire global d'exceptions pour éliminer la duplication
 * de gestion d'erreurs dans tous les Resources.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {

        // BadRequest - erreurs de validation
        if (exception instanceof BadRequestException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("BAD_REQUEST", exception.getMessage(), 400))
                    .build();
        }

        // NotFound
        if (exception instanceof NotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("NOT_FOUND", "Resource not found", 404))
                    .build();
        }

        // IllegalArgumentException - erreurs métier
        if (exception instanceof IllegalArgumentException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("BAD_REQUEST", exception.getMessage(), 400))
                    .build();
        }

        // IllegalStateException - erreurs d'état métier
        if (exception instanceof IllegalStateException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("CONFLICT", exception.getMessage(), 409))
                    .build();
        }

        // WebApplicationException - preserve the original HTTP status
        if (exception instanceof WebApplicationException wae) {
            int statusCode = wae.getResponse().getStatus();
            return Response.status(statusCode)
                    .entity(new ErrorResponse("REQUEST_FAILED", exception.getMessage(), statusCode))
                    .build();
        }

        // Erreurs internes - ne pas exposer les détails
        LOG.error("Unhandled exception", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("INTERNAL_ERROR", "An internal error occurred", 500))
                .build();
    }
}
