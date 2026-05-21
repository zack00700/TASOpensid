package fr.alb.resources;

import fr.alb.billing.dao.InvoiceDaoImpl;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public class DebugResource {
    @Path("debug")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public static class DebugResources {

        @Inject
        InvoiceDaoImpl invoiceDao;

        @GET
        @Path("debug-active-contracts")
        public Response debugStatusComparison() {
            try {
                invoiceDao.debugStatusComparison();
                return Response.ok()
                        .entity(Map.of("status", "success", "message", "Check logs for active contracts debug"))
                        .build();
            } catch (Exception e) {
                return Response.status(500)
                        .entity(Map.of("status", "error", "message", e.getMessage()))
                        .build();
            }
        }
    }
}
