package fr.alb.api;

import fr.alb.dto.ErrorResponse;
import fr.alb.model.User;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import fr.alb.service.AuthService;

import java.util.Map;
import java.util.Set;

public class CreateAdmin {
    @POST
    @Path("create-admin")
    public Response createAdmin() {
        try {
            Set<String> adminRoles = Set.of("ROLE_ADMIN", "ROLE_INVOICE_ADMIN", "ROLE_TEMPLATES_ADMIN");
            AuthService authService = new AuthService();
            User admin = authService.createUser(
                    "admin",
                    "admin@example.com",
                    "admin123!",
                    "Administrator",
                    adminRoles
            );
            return Response.ok(Map.of("message", "Admin créé", "id", admin.getId())).build();
        } catch (Exception e) {
            return Response.status(400).entity(new ErrorResponse("BAD_REQUEST", e.getMessage(), 400)).build();
        }
    }
}
