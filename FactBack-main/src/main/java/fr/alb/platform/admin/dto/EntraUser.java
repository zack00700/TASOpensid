package fr.alb.platform.admin.dto;

import java.util.List;

/**
 * Flat view of an Entra user as needed by the admin UI. Derived from the
 * Graph {@code /users} payload plus a pre-computed list of roles the user
 * holds (mapped from Entra group memberships via
 * {@code azure.ad.group.mapping.*}).
 */
public record EntraUser(
        String id,
        String displayName,
        String userPrincipalName,
        String mail,
        String jobTitle,
        boolean accountEnabled,
        String userType,
        List<String> roles
) {}
