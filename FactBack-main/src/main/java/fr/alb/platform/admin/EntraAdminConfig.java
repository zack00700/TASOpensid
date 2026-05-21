package fr.alb.platform.admin;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

/**
 * Config surface for the Microsoft Graph admin integration.
 *
 * <p>Keep the values in {@code application.properties} / environment variables
 * so credential rotation (or tenant migration) never requires a code change.
 * The matching environment variables are {@code AZURE_TENANT_ID},
 * {@code AZURE_GRAPH_CLIENT_ID}, {@code AZURE_GRAPH_INVITE_REDIRECT_URL} and
 * {@code AZURE_GRAPH_ENABLED}.
 */
@ConfigMapping(prefix = "azure.graph")
public interface EntraAdminConfig {

    /** Entra tenant that authenticates admin calls. */
    Optional<String> tenantId();

    /**
     * Client ID of the backend app registration. Matches the {@code client-id}
     * advertised by the federated credential attached to the Container App's
     * managed identity — no client secret is stored anywhere.
     */
    Optional<String> clientId();

    /** URL the invited guest is redirected to after accepting the invitation. */
    String inviteRedirectUrl();

    /**
     * Feature switch. When {@code false} (typical in dev without Azure access)
     * the admin endpoints return {@code 503 Service Unavailable} rather than
     * crashing on the first Graph call.
     */
    boolean enabled();
}
