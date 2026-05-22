package fr.alb.platform.admin.service;

import com.microsoft.graph.models.Invitation;
import com.microsoft.graph.models.InvitedUserMessageInfo;
import com.microsoft.graph.models.ReferenceCreate;
import com.microsoft.graph.models.User;
import com.microsoft.graph.models.UserCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import fr.alb.platform.admin.EntraAdminConfig;
import fr.alb.platform.admin.dto.EntraUser;
import fr.alb.platform.admin.dto.InviteRequest;
import fr.alb.service.AzureGroupRoleMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin operations against the Entra tenant.
 *
 * <p>Role membership is modelled as group membership: each application role
 * ({@code ROLE_ADMIN}, {@code ROLE_USER}, …) maps to one Entra group via the
 * {@code azure.ad.group.mapping.*} properties already consumed by
 * {@code AzureGroupRoleAugmentor} at login. Assigning a role = adding the
 * user to the matching group.
 */
@ApplicationScoped
public class EntraUserService {

    private static final Logger LOG = Logger.getLogger(EntraUserService.class);

    @Inject
    GraphServiceClient graph;

    @Inject
    EntraAdminConfig config;

    /** Reused from the existing OIDC augmentor — single source of truth for role ↔ group IDs. */
    @Inject
    AzureGroupRoleMapper groupMapper;

    /** Admin-facing list of users with their roles resolved. */
    public List<EntraUser> listUsers() {
        ensureEnabled();
        try {
            UserCollectionResponse page = graph.users().get(cfg -> {
                cfg.queryParameters.top = 500;
                cfg.queryParameters.select = new String[] {
                        "id", "displayName", "userPrincipalName", "mail",
                        "jobTitle", "accountEnabled", "userType"
                };
            });
            List<User> raw = page != null && page.getValue() != null ? page.getValue() : List.of();
            Map<String, List<String>> rolesByUserId = rolesByUserId();

            List<EntraUser> out = new ArrayList<>(raw.size());
            for (User u : raw) {
                String id = u.getId();
                out.add(new EntraUser(
                        id,
                        u.getDisplayName(),
                        u.getUserPrincipalName(),
                        u.getMail(),
                        u.getJobTitle(),
                        Boolean.TRUE.equals(u.getAccountEnabled()),
                        u.getUserType(),
                        rolesByUserId.getOrDefault(id, List.of())));
            }
            return out;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to list Entra users");
            throw new ServiceUnavailableException("Cannot reach Microsoft Graph right now.");
        }
    }

    public EntraUser addRole(String userId, String role) {
        ensureEnabled();
        String groupId = requireGroupId(role);
        try {
            ReferenceCreate ref = new ReferenceCreate();
            ref.setOdataId("https://graph.microsoft.com/v1.0/directoryObjects/" + userId);
            graph.groups().byGroupId(groupId).members().ref().post(ref);
        } catch (Exception e) {
            // Graph returns 400 when the user is already a member — treat as idempotent.
            if (!isAlreadyMember(e)) {
                LOG.errorf(e, "Failed to add user %s to role %s (group %s)", userId, role, groupId);
                throw new ServiceUnavailableException("Cannot update role on Microsoft Graph right now.");
            }
        }
        return refreshed(userId);
    }

    public EntraUser removeRole(String userId, String role) {
        ensureEnabled();
        String groupId = requireGroupId(role);
        try {
            graph.groups().byGroupId(groupId).members()
                    .byDirectoryObjectId(userId).ref().delete();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to remove user %s from role %s (group %s)", userId, role, groupId);
            throw new ServiceUnavailableException("Cannot update role on Microsoft Graph right now.");
        }
        return refreshed(userId);
    }

    public EntraUser setEnabled(String userId, boolean enabled) {
        ensureEnabled();
        try {
            User patch = new User();
            patch.setAccountEnabled(enabled);
            graph.users().byUserId(userId).patch(patch);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to toggle account for user %s -> %s", userId, enabled);
            throw new ServiceUnavailableException("Cannot update user on Microsoft Graph right now.");
        }
        return refreshed(userId);
    }

    /**
     * Patches the mutable profile fields of an Entra user (display name and job title).
     * Email/userPrincipalName are intentionally not mutable here — those are managed in Entra itself.
     */
    public EntraUser updateProfile(String userId, String displayName, String jobTitle) {
        ensureEnabled();
        try {
            User patch = new User();
            if (displayName != null) patch.setDisplayName(displayName);
            if (jobTitle != null) patch.setJobTitle(jobTitle);
            graph.users().byUserId(userId).patch(patch);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to update profile for user %s", userId);
            throw new ServiceUnavailableException("Cannot update user on Microsoft Graph right now.");
        }
        return refreshed(userId);
    }

    /**
     * Invites an external email address as a guest user and (optionally)
     * adds them to the requested role groups immediately — the role stays
     * attached even before the user redeems the invitation.
     */
    public EntraUser invite(InviteRequest req) {
        ensureEnabled();
        if (req == null || req.email() == null || req.email().isBlank()) {
            throw new BadRequestException("email is required");
        }
        try {
            Invitation invitation = new Invitation();
            invitation.setInvitedUserEmailAddress(req.email());
            if (req.displayName() != null && !req.displayName().isBlank()) {
                invitation.setInvitedUserDisplayName(req.displayName());
            }
            invitation.setInviteRedirectUrl(config.inviteRedirectUrl());
            invitation.setSendInvitationMessage(Boolean.TRUE);

            InvitedUserMessageInfo message = new InvitedUserMessageInfo();
            message.setCustomizedMessageBody(
                    "You have been invited to the FactBack terminal operations portal.");
            invitation.setInvitedUserMessageInfo(message);

            Invitation created = graph.invitations().post(invitation);
            String userId = created != null && created.getInvitedUser() != null
                    ? created.getInvitedUser().getId() : null;
            if (userId == null) {
                throw new ServiceUnavailableException("Graph did not return an invited user id.");
            }
            if (req.roles() != null) {
                for (String role : req.roles()) {
                    try { addRole(userId, role); }
                    catch (Exception inner) { LOG.warnf(inner, "Could not pre-assign role %s to invited user %s", role, userId); }
                }
            }
            return refreshed(userId);
        } catch (BadRequestException | ServiceUnavailableException pass) {
            throw pass;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to invite %s", req.email());
            throw new ServiceUnavailableException("Cannot send invitation via Microsoft Graph right now.");
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private void ensureEnabled() {
        if (!config.enabled()) {
            throw new ServiceUnavailableException(
                    "Entra admin integration is disabled (set azure.graph.enabled=true).");
        }
    }

    private String requireGroupId(String role) {
        String id = groupMapper.getRoleToGroupIdMap().get(role);
        if (id == null || id.isBlank()) {
            throw new BadRequestException("Unknown role '" + role
                    + "' — no mapping under azure.ad.group.mapping.*");
        }
        return id;
    }

    /**
     * Build the reverse index {@code userId → [roles]} by fetching the members
     * of every role group once. N=5 API calls regardless of tenant size, vs
     * one {@code /memberOf} call per user which would scale linearly.
     */
    private Map<String, List<String>> rolesByUserId() {
        Map<String, List<String>> out = new HashMap<>();
        for (Map.Entry<String, String> e : groupMapper.getRoleToGroupIdMap().entrySet()) {
            String role = e.getKey();
            String groupId = e.getValue();
            if (groupId == null || groupId.isBlank()) continue;
            try {
                var page = graph.groups().byGroupId(groupId).members().get(cfg -> {
                    cfg.queryParameters.top = 999;
                    cfg.queryParameters.select = new String[] { "id" };
                });
                if (page != null && page.getValue() != null) {
                    for (var member : page.getValue()) {
                        String id = member.getId();
                        if (id == null) continue;
                        out.computeIfAbsent(id, k -> new ArrayList<>()).add(role);
                    }
                }
            } catch (Exception ex) {
                LOG.warnf(ex, "Could not list members of role %s (group %s) — row will show no role",
                        role, groupId);
            }
        }
        return out;
    }

    private EntraUser refreshed(String userId) {
        try {
            User u = graph.users().byUserId(userId).get(cfg -> cfg.queryParameters.select = new String[] {
                    "id", "displayName", "userPrincipalName", "mail",
                    "jobTitle", "accountEnabled", "userType"
            });
            if (u == null) throw new NotFoundException("User " + userId + " not found after update.");
            List<String> roles = rolesByUserId().getOrDefault(userId, List.of());
            return new EntraUser(
                    u.getId(), u.getDisplayName(), u.getUserPrincipalName(), u.getMail(),
                    u.getJobTitle(), Boolean.TRUE.equals(u.getAccountEnabled()),
                    u.getUserType(), roles);
        } catch (NotFoundException nf) {
            throw nf;
        } catch (Exception e) {
            LOG.warnf(e, "Could not refresh user %s after update", userId);
            return new EntraUser(userId, null, null, null, null, false, null, Collections.emptyList());
        }
    }

    private static boolean isAlreadyMember(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("already exist") || msg.contains("One or more added object references already exist");
    }

    /** Exposed so the resource can enumerate the role catalogue for the UI. */
    public List<String> knownRoles() {
        List<String> roles = new ArrayList<>(groupMapper.getRoleToGroupIdMap().keySet());
        Collections.sort(roles);
        return roles;
    }
}
