package fr.alb.filter;

import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.security.Principal;

/**
 * HTTP Request/Response logging filter for monitoring and debugging.
 * Logs incoming requests and outgoing responses with timing information.
 */
@Provider
public class RequestResponseLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(RequestResponseLoggingFilter.class);
    private static final String REQUEST_START_TIME = "REQUEST_START_TIME";

    @Context
    UriInfo uriInfo;

    @Context
    HttpServerRequest request;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Record request start time
        requestContext.setProperty(REQUEST_START_TIME, System.currentTimeMillis());

        // Log request details
        if (LOG.isDebugEnabled()) {
            String method = requestContext.getMethod();
            String path = requestContext.getUriInfo().getPath();
            String clientIp = getClientIp();
            String user = getUserIdentifier(requestContext);

            LOG.debugf("Incoming request: %s %s from %s (user: %s)",
                method, path, clientIp, user);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                      ContainerResponseContext responseContext) throws IOException {
        // Calculate request duration
        Long startTime = (Long) requestContext.getProperty(REQUEST_START_TIME);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getPath();
        int status = responseContext.getStatus();
        String clientIp = getClientIp();
        String user = getUserIdentifier(requestContext);

        // Log response with appropriate level based on status code
        if (status >= 500) {
            LOG.errorf("Request completed: %s %s -> %d (%dms) from %s (user: %s)",
                method, path, status, duration, clientIp, user);
        } else if (status >= 400) {
            LOG.warnf("Request completed: %s %s -> %d (%dms) from %s (user: %s)",
                method, path, status, duration, clientIp, user);
        } else if (LOG.isInfoEnabled()) {
            LOG.infof("Request completed: %s %s -> %d (%dms) from %s (user: %s)",
                method, path, status, duration, clientIp, user);
        }

        // Log slow requests
        if (duration > 1000) {
            LOG.warnf("Slow request detected: %s %s took %dms (user: %s)",
                method, path, duration, user);
        }
    }

    private String getClientIp() {
        if (request != null) {
            // Check for forwarded headers (load balancer/proxy)
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isEmpty()) {
                return forwardedFor.split(",")[0].trim();
            }

            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isEmpty()) {
                return realIp;
            }

            return request.remoteAddress().host();
        }
        return "unknown";
    }

    private String getUserIdentifier(ContainerRequestContext requestContext) {
        Principal principal = requestContext.getSecurityContext().getUserPrincipal();
        return principal != null ? principal.getName() : "anonymous";
    }
}
