package fr.alb.platform.admin.resource;

import fr.alb.platform.admin.EntraAdminConfig;
import fr.alb.platform.admin.dto.EntraUser;
import fr.alb.platform.admin.dto.InviteRequest;
import fr.alb.platform.admin.service.EntraUserService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/**
 * Admin surface for the Entra-backed user directory.
 *
 * <ul>
 *     <li>{@code GET  /admin/users/status} — read-only feature toggle (no PII).</li>
 *     <li>{@code GET  /admin/users} — listing with resolved roles.</li>
 *     <li>{@code GET  /admin/users/roles} — role catalogue for dropdowns.</li>
 *     <li>{@code POST /admin/users/invite} — send an invitation.</li>
 *     <li>{@code PATCH /admin/users/{id}} — update mutable profile fields.</li>
 *     <li>{@code POST /admin/users/{id}/roles/{role}} — add role.</li>
 *     <li>{@code DELETE /admin/users/{id}/roles/{role}} — remove role.</li>
 *     <li>{@code POST /admin/users/{id}/enable} — enable account.</li>
 *     <li>{@code POST /admin/users/{id}/disable} — disable account.</li>
 * </ul>
 *
 * Every endpoint is restricted to {@code ROLE_ADMIN} except {@code /status} which
 * is intentionally public — it exposes only the boolean {@code enabled} flag.
 */
@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ROLE_ADMIN")
public class UserAdminResource {

    @Inject
    EntraUserService users;

    @Inject
    EntraAdminConfig adminConfig;

    /** Lightweight status probe so the UI can hide invite/edit buttons when Graph is off. */
    @GET
    @Path("status")
    @PermitAll
    public Map<String, Object> status() {
        return Map.of("enabled", adminConfig.enabled());
    }

    @GET
    public List<EntraUser> list() {
        return users.listUsers();
    }

    @GET
    @Path("roles")
    public List<String> roles() {
        return users.knownRoles();
    }

    @POST
    @Path("invite")
    public EntraUser invite(InviteRequest request) {
        return users.invite(request);
    }

    @POST
    @Path("{id}/roles/{role}")
    public EntraUser addRole(@PathParam("id") String userId, @PathParam("role") String role) {
        return users.addRole(userId, role);
    }

    @DELETE
    @Path("{id}/roles/{role}")
    public EntraUser removeRole(@PathParam("id") String userId, @PathParam("role") String role) {
        return users.removeRole(userId, role);
    }

    @POST
    @Path("{id}/enable")
    public EntraUser enable(@PathParam("id") String userId) {
        return users.setEnabled(userId, true);
    }

    @POST
    @Path("{id}/disable")
    public EntraUser disable(@PathParam("id") String userId) {
        return users.setEnabled(userId, false);
    }

    /** Updates the mutable Entra profile fields (displayName, jobTitle) — TC-12. */
    @PATCH
    @Path("{id}")
    public EntraUser updateProfile(@PathParam("id") String userId, UpdateProfileRequest req) {
        if (req == null) {
            throw new BadRequestException("request body required");
        }
        return users.updateProfile(userId, req.displayName, req.jobTitle);
    }

    public static class UpdateProfileRequest {
        public String displayName;
        public String jobTitle;
    }
}
