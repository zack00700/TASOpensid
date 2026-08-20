package fr.alb.billing.service;

import fr.alb.billing.model.InvoiceTemplate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class InvoiceTemplateSeederTest {

    @Inject
    InvoiceTemplateSeeder seeder;

    @BeforeEach
    void clean() {
        InvoiceTemplate.deleteAll();
    }

    private InvoiceTemplate findActive(String type) {
        return InvoiceTemplate
            .<InvoiceTemplate>find("status = ?1 and type = ?2", "active", type)
            .firstResult();
    }

    private InvoiceTemplate persistTemplate(String type, String status, String html, String css) {
        InvoiceTemplate t = new InvoiceTemplate("Test " + type);
        t.type = type;
        t.status = status;
        t.template = new InvoiceTemplate.TemplateContent(html, css);
        t.persist();
        return t;
    }

    @Test
    void onStart_createsActiveDraftAndFinal_whenNoneExist() {
        seeder.onStart(null);

        InvoiceTemplate draft = findActive("draft");
        InvoiceTemplate finalT = findActive("final");
        assertNotNull(draft, "draft template should be created");
        assertNotNull(finalT, "final template should be created");
        assertTrue(draft.template.html.contains("PROJET DE FACTURE"),
            "draft template should carry the new content marker");
        assertTrue(finalT.template.html.contains("PROJET DE FACTURE"),
            "final template should carry the same content (status switch handled by isDraft in the template)");
    }

    @Test
    void onStart_replacesPlaceholderInPlace_preservingId() {
        InvoiceTemplate placeholder = persistTemplate(
            "draft", "active",
            "<div>INVOICE Nº {{draftNumber}}<br/>Thank you for your business</div>",
            "body { font-family: Arial; }");
        String idBefore = placeholder.id;

        seeder.onStart(null);

        InvoiceTemplate draft = findActive("draft");
        assertEquals(idBefore, draft.id, "id should be preserved so invoices keep pointing at it");
        assertFalse(draft.template.html.contains("Thank you for your business"),
            "placeholder content should be gone");
        assertTrue(draft.template.html.contains("PROJET DE FACTURE"),
            "new content should be in place");
    }

    @Test
    void onStart_leavesCustomisedTemplatesAlone() {
        InvoiceTemplate custom = persistTemplate(
            "draft", "active",
            "<div class='custom'>My very own draft layout for {{customerName}}</div>",
            ".custom { color: red; }");
        String htmlBefore = custom.template.html;

        seeder.onStart(null);

        InvoiceTemplate draft = findActive("draft");
        assertEquals(htmlBefore, draft.template.html,
            "customised template content must not be overwritten");
    }

    @Test
    void onStart_isIdempotent() {
        seeder.onStart(null);
        InvoiceTemplate first = findActive("draft");
        long countAfterFirst = InvoiceTemplate.count();

        seeder.onStart(null);
        InvoiceTemplate second = findActive("draft");

        assertEquals(first.id, second.id, "second run should not create a duplicate");
        assertEquals(countAfterFirst, InvoiceTemplate.count(),
            "second run should not change the template count");
        assertEquals(first.template.html, second.template.html,
            "second run should not re-write the html");
    }

    @Test
    void isPlaceholder_detectsKnownFingerprints() {
        InvoiceTemplate t1 = new InvoiceTemplate("x");
        t1.template = new InvoiceTemplate.TemplateContent("<p>Thank you for your business</p>", "");
        assertTrue(InvoiceTemplateSeeder.isPlaceholder(t1));

        InvoiceTemplate t2 = new InvoiceTemplate("x");
        t2.template = new InvoiceTemplate.TemplateContent("<p>Generated on 2026-05-25</p>", "");
        assertTrue(InvoiceTemplateSeeder.isPlaceholder(t2));

        InvoiceTemplate t3 = new InvoiceTemplate("x");
        t3.template = new InvoiceTemplate.TemplateContent("<h1>Port & Logistics Services</h1>", "");
        assertTrue(InvoiceTemplateSeeder.isPlaceholder(t3));
    }

    @Test
    void isPlaceholder_treatsEmptyOrNullHtmlAsPlaceholder() {
        InvoiceTemplate t1 = new InvoiceTemplate("x");
        t1.template = new InvoiceTemplate.TemplateContent(null, "");
        assertTrue(InvoiceTemplateSeeder.isPlaceholder(t1));

        InvoiceTemplate t2 = new InvoiceTemplate("x");
        t2.template = new InvoiceTemplate.TemplateContent("   ", "");
        assertTrue(InvoiceTemplateSeeder.isPlaceholder(t2));

        InvoiceTemplate t3 = new InvoiceTemplate("x");
        t3.template = null;
        assertTrue(InvoiceTemplateSeeder.isPlaceholder(t3));
    }

    @Test
    void isPlaceholder_false_forArbitraryCustomContent() {
        InvoiceTemplate t = new InvoiceTemplate("x");
        t.template = new InvoiceTemplate.TemplateContent(
            "<div>Custom invoice for {{customerName}}</div>", "");
        assertFalse(InvoiceTemplateSeeder.isPlaceholder(t));
    }

    @Test
    void isPlaceholder_treatsPreviousSeedAsPlaceholder() {
        InvoiceTemplate t = new InvoiceTemplate("x");
        t.template = new InvoiceTemplate.TemplateContent(
            "<div class=\"draft-banner\">⚠ DOCUMENT NON DÉFINITIF — BROUILLON DE FACTURE</div>",
            "");
        // v1 marker present, v2 marker absent → upgrade.
        assertTrue(InvoiceTemplateSeeder.isPlaceholder(t));
    }

    @Test
    void isPlaceholder_treatsCurrentSeedAsNotPlaceholder_evenWithV1Strings() {
        // The new seed re-uses the same user-facing strings as v1, so the
        // v2 marker is what we use to decide "leave alone".
        InvoiceTemplate t = new InvoiceTemplate("x");
        t.template = new InvoiceTemplate.TemplateContent(
            "<!-- factback:seed:v2 -->\n"
                + "<div class=\"draft-banner\">⚠ DOCUMENT NON DÉFINITIF — BROUILLON DE FACTURE</div>",
            "");
        assertFalse(InvoiceTemplateSeeder.isPlaceholder(t));
    }
}
