package fr.alb.billing.resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import java.util.Comparator;

import fr.alb.billing.model.InvoiceTemplate;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

/**
 * One-shot admin endpoint to migrate legacy InvoiceTemplate documents to the typed model.
 *
 * Steps:
 *   1. For every template without a `type` field, set type='final' (most common usage).
 *   2. Among templates that ended up with status='active' AND type='final', keep only
 *      the most recently updated as active; archive the rest.
 *
 * Idempotent: re-running it after migration is a no-op (no templates with null type,
 * at most one active template per type).
 */
@Path("/admin/invoice-templates")
@RunOnVirtualThread
public class AdminInvoiceTemplateMigrationResource {

    private static final Logger LOG = Logger.getLogger(AdminInvoiceTemplateMigrationResource.class);

    @POST
    @Path("migrate-to-typed")
    @RolesAllowed({"ROLE_ADMIN"})
    public Response migrateToTyped() {
        long updatedType = 0;
        long demoted = 0;

        // Step 1: assign type='final' to any template missing it.
        List<InvoiceTemplate> untyped = InvoiceTemplate.<InvoiceTemplate>find("type is null").list();
        for (InvoiceTemplate t : untyped) {
            t.type = "final";
            t.updateTimestamp();
            t.update();
            updatedType++;
        }
        LOG.infof("migrate-to-typed: assigned type='final' to %d templates without a type", updatedType);

        // Step 2: enforce "at most 1 active per type". Keep the most recently updated, archive the rest.
        // Cosmos rejects $sort on non-indexed fields (updatedAt is excluded), so fetch and sort in Java.
        for (String type : List.of("draft", "final")) {
            List<InvoiceTemplate> actives = InvoiceTemplate
                    .<InvoiceTemplate>find("status = ?1 and type = ?2", "active", type)
                    .list();
            if (actives.size() > 1) {
                actives.sort(Comparator.comparing(
                        (InvoiceTemplate t) -> t.updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
                LOG.infof("migrate-to-typed: %d active templates for type=%s, keeping the most recent (%s)",
                        actives.size(), type, actives.get(0).id);
                for (int i = 1; i < actives.size(); i++) {
                    InvoiceTemplate t = actives.get(i);
                    t.status = "archived";
                    t.updateTimestamp();
                    t.update();
                    demoted++;
                }
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("typeAssigned", updatedType);
        body.put("demoted", demoted);
        LOG.infof("migrate-to-typed: typeAssigned=%d demoted=%d", updatedType, demoted);
        return Response.ok(body).build();
    }
}
