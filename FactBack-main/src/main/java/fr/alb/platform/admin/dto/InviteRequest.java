package fr.alb.platform.admin.dto;

import java.util.List;

/**
 * Body of {@code POST /admin/users/invite}.
 *
 * @param email       address the invitation is sent to
 * @param displayName friendly name to show in the admin list and the email
 * @param roles       initial role group names (e.g. {@code ["ROLE_USER"]})
 *                    to add the user to right after they redeem the invite
 */
public record InviteRequest(
        String email,
        String displayName,
        List<String> roles
) {}
