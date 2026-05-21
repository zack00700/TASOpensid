package fr.alb.platform.admin;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/**
 * Produces the singleton {@link GraphServiceClient} used by the admin
 * services.
 *
 * <p>Authentication uses {@link DefaultAzureCredential}, which on Container
 * Apps picks up the managed identity automatically (federated credential on
 * the Entra app registration referenced by {@link EntraAdminConfig#clientId()})
 * — no secret to rotate. Locally, {@code az login} is picked up instead.
 */
@ApplicationScoped
public class GraphClientProvider {

    private static final Logger LOG = Logger.getLogger(GraphClientProvider.class);
    private static final String[] GRAPH_DEFAULT_SCOPE = { "https://graph.microsoft.com/.default" };

    @Inject
    EntraAdminConfig config;

    /**
     * {@code @Singleton} (not {@code @ApplicationScoped}) because
     * {@link GraphServiceClient} is a final-ish SDK class without a public
     * no-args constructor — Arc can't build a client proxy for it. Singleton
     * beans aren't proxied, which is exactly what we want here.
     */
    @Produces
    @Singleton
    GraphServiceClient graphClient() {
        if (!config.enabled()) {
            LOG.warn("Entra admin is disabled (azure.graph.enabled=false). Returning a client that will "
                    + "fail on first call — expected in dev.");
        }
        DefaultAzureCredentialBuilder builder = new DefaultAzureCredentialBuilder();
        config.tenantId().filter(s -> !s.isBlank()).ifPresent(builder::tenantId);
        config.clientId().filter(s -> !s.isBlank()).ifPresent(builder::managedIdentityClientId);
        DefaultAzureCredential credential = builder.build();
        return new GraphServiceClient(credential, GRAPH_DEFAULT_SCOPE);
    }
}
