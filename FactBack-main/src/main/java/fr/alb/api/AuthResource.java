package fr.alb.api;

import java.util.Map;
import java.util.Set;

import fr.alb.dto.ErrorResponse;
import fr.alb.service.AuthService;
import fr.alb.model.User;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(@Valid LoginRequest request, @Context ContainerRequestContext httpRequest) {
        try {
            String clientIp = getClientIp(httpRequest);
            AuthService.AuthResult result = authService.authenticate(
                    request.username,
                    request.password,
                    clientIp
            );

            if (result.isSuccess()) {
                Map<String, Object> response = Map.of(
                        "token", result.getToken(),
                        "user", Map.of(
                                "id", result.getUser().getId(),
                                "username", result.getUser().username,
                                "email", result.getUser().email,
                                "fullName", result.getUser().fullName,
                                "roles", result.getUser().roles
                        )
                );
                return Response.ok(response).build();
            } else {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(new ErrorResponse("UNAUTHORIZED", result.getErrorMessage(), 401))
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("INTERNAL_ERROR", "Erreur d'authentification", 500))
                    .build();
        }
    }


    @POST
    @Path("/register")
    public Response register(@Valid RegisterRequest request) {
        try {
            Set<String> defaultRoles = Set.of("ROLE_USER");
            User user = authService.createUser(
                    request.username,
                    request.email,
                    request.password,
                    request.fullName,
                    defaultRoles
            );

            Map<String, Object> response = Map.of(
                    "message", "Utilisateur créé avec succès",
                    "userId", user.getId()
            );

            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("BAD_REQUEST", e.getMessage(), 400))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("INTERNAL_ERROR", "Erreur lors de la création du compte", 500))
                    .build();
        }
    }

    @POST
    @Path("/forgot-password")
    public Response forgotPassword(@Valid ForgotPasswordRequest request) {
        try {
            AuthService.PasswordResetResult result = authService.requestPasswordReset(request.email);

            // Always return 200 to prevent email enumeration
            // The actual token is returned in the response (for testing/development)
            // In production, this would be sent via email instead
            Map<String, Object> response = Map.of(
                    "message", result.getMessage(),
                    "token", result.getToken() != null ? result.getToken() : ""
            );

            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.error("Error requesting password reset", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("INTERNAL_ERROR", "Erreur lors de la demande de réinitialisation", 500))
                    .build();
        }
    }

    @POST
    @Path("/reset-password")
    public Response resetPassword(@Valid ResetPasswordRequest request) {
        try {
            AuthService.ResetPasswordResult result = authService.resetPassword(
                    request.token,
                    request.newPassword
            );

            if (result.isSuccess()) {
                return Response.ok(Map.of("message", result.getMessage())).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("BAD_REQUEST", result.getMessage(), 400))
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("INTERNAL_ERROR", "Erreur lors de la réinitialisation du mot de passe", 500))
                    .build();
        }
    }

    @GET
    @Path("/validate-reset-token/{token}")
    public Response validateResetToken(@PathParam("token") String token) {
        try {
            AuthService.TokenValidationResult result = authService.validateResetToken(token);

            if (result.isValid()) {
                User user = result.getUser();
                return Response.ok(Map.of(
                        "valid", true,
                        "email", result.getEmail(),
                        "user", Map.of(
                                "id", user.getId(),
                                "username", user.username,
                                "email", user.email,
                                "fullName", user.fullName
                        ),
                        "message", result.getMessage()
                )).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of(
                                "valid", false,
                                "message", result.getMessage()
                        ))
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("INTERNAL_ERROR", "Erreur lors de la validation du token", 500))
                    .build();
        }
    }

    private String getClientIp(ContainerRequestContext requestContext) {
        String xForwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = requestContext.getHeaderString("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return getRemoteAddressFromRequest(requestContext);
    }

    private String getRemoteAddressFromRequest(ContainerRequestContext requestContext) {
        // This is a workaround since ContainerRequestContext doesn't have remote address
        // You might need to use a filter to capture this information
        String remoteAddr = requestContext.getHeaderString("X-Forwarded-For");
        if (remoteAddr != null) return remoteAddr.split(",")[0];

        remoteAddr = requestContext.getHeaderString("Remote_Addr");
        if (remoteAddr != null) return remoteAddr;

        return "unknown"; // Fallback
    }

    // DTOs pour les requêtes
    public static class LoginRequest {
        @NotBlank(message = "Username requis")
        public String username;

        @NotBlank(message = "Mot de passe requis")
        public String password;
    }

    public static class RegisterRequest {
        @NotBlank(message = "Username requis")
        @Size(min = 3, max = 50, message = "Username entre 3 et 50 caractères")
        public String username;

        @NotBlank(message = "Email requis")
        @Email(message = "Format email invalide")
        public String email;

        @NotBlank(message = "Mot de passe requis")
        @Size(min = 8, message = "Mot de passe minimum 8 caractères")
        public String password;

        @NotBlank(message = "Nom complet requis")
        public String fullName;
    }

    public static class ForgotPasswordRequest {
        @NotBlank(message = "Email requis")
        @Email(message = "Format email invalide")
        public String email;
    }

    public static class ResetPasswordRequest {
        @NotBlank(message = "Token requis")
        public String token;

        @NotBlank(message = "Mot de passe requis")
        @Size(min = 8, message = "Mot de passe minimum 8 caractères")
        public String newPassword;
    }
}
