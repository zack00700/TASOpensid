package fr.alb.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Maps Azure AD Group Object IDs to role names used by the application.
 * This allows using Azure AD security groups for role-based access control.
 */
@ApplicationScoped
public class AzureGroupRoleMapper {

    private static final Logger LOG = Logger.getLogger(AzureGroupRoleMapper.class);

    @ConfigProperty(name = "azure.ad.group.mapping.ROLE_ADMIN", defaultValue = "")
    Optional<String> adminGroupId;

    @ConfigProperty(name = "azure.ad.group.mapping.ROLE_INVOICE_ADMIN", defaultValue = "")
    Optional<String> invoiceAdminGroupId;

    @ConfigProperty(name = "azure.ad.group.mapping.ROLE_TEMPLATES_ADMIN", defaultValue = "")
    Optional<String> templatesAdminGroupId;

    @ConfigProperty(name = "azure.ad.group.mapping.ROLE_USER", defaultValue = "")
    Optional<String> userGroupId;

    @ConfigProperty(name = "azure.ad.group.mapping.ROLE_READONLY", defaultValue = "")
    Optional<String> readonlyGroupId;

    private final Map<String, String> groupIdToRoleMap = new HashMap<>();

    @PostConstruct
    void init() {
        adminGroupId.filter(id -> !id.isEmpty() && !id.startsWith("<"))
            .ifPresent(id -> groupIdToRoleMap.put(id, "ROLE_ADMIN"));

        invoiceAdminGroupId.filter(id -> !id.isEmpty() && !id.startsWith("<"))
            .ifPresent(id -> groupIdToRoleMap.put(id, "ROLE_INVOICE_ADMIN"));

        templatesAdminGroupId.filter(id -> !id.isEmpty() && !id.startsWith("<"))
            .ifPresent(id -> groupIdToRoleMap.put(id, "ROLE_TEMPLATES_ADMIN"));

        userGroupId.filter(id -> !id.isEmpty() && !id.startsWith("<"))
            .ifPresent(id -> groupIdToRoleMap.put(id, "ROLE_USER"));

        readonlyGroupId.filter(id -> !id.isEmpty() && !id.startsWith("<"))
            .ifPresent(id -> groupIdToRoleMap.put(id, "ROLE_READONLY"));

        if (groupIdToRoleMap.isEmpty()) {
            LOG.warn("No Azure AD group mappings configured. Groups will be used as-is for role names.");
        } else {
            LOG.info("Azure AD group mappings configured: " + groupIdToRoleMap.size() + " groups mapped");
        }
    }

    /**
     * Maps a single Azure AD group Object ID to a role name.
     * If no mapping exists, returns the original group ID as the role name.
     *
     * @param groupId Azure AD group Object ID
     * @return Role name
     */
    public String mapGroupToRole(String groupId) {
        return groupIdToRoleMap.getOrDefault(groupId, groupId);
    }

    /**
     * Maps a set of Azure AD group Object IDs to role names.
     *
     * @param groupIds Set of Azure AD group Object IDs
     * @return Set of role names
     */
    public Set<String> mapGroupsToRoles(Set<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Set.of();
        }

        return groupIds.stream()
            .map(this::mapGroupToRole)
            .collect(Collectors.toSet());
    }

    /**
     * Checks if Azure AD group mapping is enabled.
     *
     * @return true if at least one group mapping is configured
     */
    public boolean isMappingEnabled() {
        return !groupIdToRoleMap.isEmpty();
    }

    /**
     * @return an unmodifiable view of the configured {@code role → groupId}
     *         mappings. Useful for admin code that needs to manipulate group
     *         membership (e.g. assigning a role via Microsoft Graph).
     */
    public Map<String, String> getRoleToGroupIdMap() {
        Map<String, String> reverse = new HashMap<>();
        for (Map.Entry<String, String> e : groupIdToRoleMap.entrySet()) {
            reverse.put(e.getValue(), e.getKey());
        }
        return Map.copyOf(reverse);
    }
}
