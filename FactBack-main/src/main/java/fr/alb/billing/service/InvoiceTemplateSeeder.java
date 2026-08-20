package fr.alb.billing.service;

import fr.alb.billing.model.InvoiceTemplate;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Seeds polished active templates for the {@code draft} and {@code final}
 * invoice types on boot.
 *
 * <p>Idempotent and conservative:
 * <ul>
 *   <li>If there is no active template for a given type, creates one.</li>
 *   <li>If there is an active template AND its HTML contains any of the known
 *       placeholder fingerprints ("Thank you for your business",
 *       "Generated on", "Port & Logistics Services"), replaces its content
 *       in-place (same id, keeps consistency for invoices already pointing
 *       at it via {@code templateId}).</li>
 *   <li>Otherwise — i.e. the operator has put their own template — leaves
 *       it alone.</li>
 * </ul>
 *
 * <p>Disable with {@code -Dinvoice.template.seed.enabled=false} if needed.
 */
@ApplicationScoped
public class InvoiceTemplateSeeder {

    private static final Logger LOG = Logger.getLogger(InvoiceTemplateSeeder.class);

    private static final String HTML_RESOURCE = "/seed/invoice-template.html";
    private static final String CSS_RESOURCE = "/seed/invoice-template.css";

    /** Marker stamped into the current seed HTML. A template carrying this
     *  marker is already up to date; we leave it alone (idempotency) even if
     *  other fingerprints also match (they will, because the new seed kept
     *  the same user-facing strings as v1). */
    static final String CURRENT_SEED_MARKER = "factback:seed:v2";

    /** Substrings that identify the very first prototype templates shipped
     *  before any default was seeded ("Thank you for your business" + a
     *  generic invoice scaffold). */
    static final List<String> PLACEHOLDER_FINGERPRINTS = Arrays.asList(
        "Thank you for your business",
        "Generated on",
        "Port & Logistics Services"
    );

    /** Substrings that uniquely identify the previous seeded default (v1,
     *  shipped in #160) so the seeder can upgrade it to v2 in place. None
     *  of these should reasonably appear in an operator-written template. */
    static final List<String> PREVIOUS_SEED_FINGERPRINTS = Arrays.asList(
        "DOCUMENT NON DÉFINITIF — BROUILLON DE FACTURE",
        "pourront être appliquées conformément aux dispositions légales en vigueur"
    );

    @ConfigProperty(name = "invoice.template.seed.enabled", defaultValue = "true")
    boolean enabled;

    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION + 1000) StartupEvent ev) {
        if (!enabled) {
            LOG.debug("InvoiceTemplateSeeder: disabled");
            return;
        }
        String html;
        String css;
        try {
            html = readResource(HTML_RESOURCE);
            css = readResource(CSS_RESOURCE);
        } catch (IOException e) {
            LOG.errorf(e, "InvoiceTemplateSeeder: cannot read seed resources, skipping");
            return;
        }

        seedType("draft", "Default draft invoice", html, css);
        seedType("final", "Default final invoice", html, css);
    }

    private void seedType(String type, String defaultName, String html, String css) {
        InvoiceTemplate active = InvoiceTemplate
            .<InvoiceTemplate>find("status = ?1 and type = ?2", "active", type)
            .firstResult();

        if (active == null) {
            // No active template for this type yet — create one.
            InvoiceTemplate t = new InvoiceTemplate(defaultName);
            t.type = type;
            t.status = "active";
            t.template = new InvoiceTemplate.TemplateContent(html, css);
            t.persist();
            LOG.infof("InvoiceTemplateSeeder: created active %s template (id=%s)", type, t.id);
            return;
        }

        if (!isPlaceholder(active)) {
            LOG.debugf("InvoiceTemplateSeeder: active %s template is customised, leaving alone (id=%s)",
                type, active.id);
            return;
        }

        // Active placeholder — replace content in-place to preserve any
        // invoices that explicitly point at this id via templateId.
        if (active.template == null) {
            active.template = new InvoiceTemplate.TemplateContent();
        }
        active.template.html = html;
        active.template.css = css;
        active.updatedAt = Instant.now();
        active.update();
        LOG.infof("InvoiceTemplateSeeder: replaced placeholder %s template content (id=%s)",
            type, active.id);
    }

    static boolean isPlaceholder(InvoiceTemplate t) {
        if (t == null || t.template == null) return true;
        String html = t.template.html;
        if (html == null || html.isBlank()) return true;
        // Already the current seed version — keep idempotency.
        if (html.contains(CURRENT_SEED_MARKER)) return false;
        for (String fp : PLACEHOLDER_FINGERPRINTS) {
            if (html.contains(fp)) return true;
        }
        // Our previous default — upgrade to the current version in place.
        for (String fp : PREVIOUS_SEED_FINGERPRINTS) {
            if (html.contains(fp)) return true;
        }
        return false;
    }

    private String readResource(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) throw new IOException("Seed resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
