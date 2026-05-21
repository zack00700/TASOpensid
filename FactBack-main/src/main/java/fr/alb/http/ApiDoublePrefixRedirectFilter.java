package fr.alb.http;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

/**
 * Redirects legacy requests that accidentally use a double /api prefix
 * (e.g. /api/api/ask-ai) to the canonical single-prefixed path.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiDoublePrefixRedirectFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) {
        final String p = ctx.getUriInfo().getRequestUri().getPath();
        if (p.startsWith("/api/api/")) {
            String target = p.replaceFirst("^/api/api/", "/api/");
            URI newUri = ctx.getUriInfo().getRequestUriBuilder()
                    .replacePath(target)
                    .build();
            ctx.abortWith(Response.status(308)
                    .location(newUri)
                    .build());
        }
    }
}

