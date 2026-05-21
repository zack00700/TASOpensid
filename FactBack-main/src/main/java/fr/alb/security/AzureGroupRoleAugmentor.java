package fr.alb.security;

import java.util.Set;
import java.util.function.Supplier;

import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;

import fr.alb.service.AzureGroupRoleMapper;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * SecurityIdentityAugmentor that maps Azure AD group Object IDs to role names.
 * This augmentor integrates with Quarkus security and works properly with @RolesAllowed annotations.
 *
 * Uses OidcJwtCallerPrincipal from quarkus-oidc instead of JsonWebToken from SmallRye JWT,
 * eliminating the need for JWT dependencies.
 */
@ApplicationScoped
public class AzureGroupRoleAugmentor implements SecurityIdentityAugmentor {

    private static final Logger LOG = Logger.getLogger(AzureGroupRoleAugmentor.class);

    @Inject
    AzureGroupRoleMapper groupRoleMapper;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        // Only process if we have a valid identity
        if (identity.isAnonymous()) {
            LOG.debug("Anonymous identity, skipping role mapping");
            return Uni.createFrom().item(identity);
        }

        // Only apply mapping if it's enabled
        if (!groupRoleMapper.isMappingEnabled()) {
            LOG.debug("Azure AD group mapping is disabled, using original roles");
            return Uni.createFrom().item(identity);
        }

        return context.runBlocking(new Supplier<SecurityIdentity>() {
            @Override
            public SecurityIdentity get() {
                try {
                    // Get the OIDC JWT token from the identity's principal
                    // In Quarkus OIDC, the principal is an OidcJwtCallerPrincipal
                    if (!(identity.getPrincipal() instanceof OidcJwtCallerPrincipal)) {
                        LOG.debug("Principal is not an OidcJwtCallerPrincipal, using original roles");
                        return identity;
                    }

                    OidcJwtCallerPrincipal oidcPrincipal = (OidcJwtCallerPrincipal) identity.getPrincipal();

                    // Extract groups from OIDC JWT token
                    Set<String> groups = oidcPrincipal.getClaim("groups");
                    if (groups == null || groups.isEmpty()) {
                        LOG.debug("No groups claim found in OIDC token for user: " + oidcPrincipal.getName());
                        return identity;
                    }

                    // Map group IDs to role names
                    Set<String> mappedRoles = groupRoleMapper.mapGroupsToRoles(groups);

                    if (mappedRoles.isEmpty()) {
                        LOG.warn("No roles mapped from " + groups.size() + " groups for user: " + identity.getPrincipal().getName());
                        return identity;
                    }

                    LOG.info("Mapped " + groups.size() + " Azure AD groups to " + mappedRoles.size() + " roles for user: "
                            + identity.getPrincipal().getName() + " - Roles: " + mappedRoles);

                    // Build a new SecurityIdentity with the mapped roles
                    QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);

                    // Add all mapped roles
                    for (String role : mappedRoles) {
                        builder.addRole(role);
                    }

                    return builder.build();

                } catch (Exception e) {
                    LOG.error("Error mapping Azure AD groups to roles for user: " + identity.getPrincipal().getName(), e);
                    // Return original identity on error to avoid breaking authentication
                    return identity;
                }
            }
        });
    }

    @Override
    public int priority() {
        // Run after other augmentors to ensure JWT is already processed
        return 100;
    }
}
