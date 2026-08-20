package fr.alb.billing.service;

import fr.alb.billing.model.Invoice;
import fr.alb.billing.model.InvoiceLineSnap;
import fr.alb.billing.model.InvoiceTemplate;
import fr.alb.billing.model.RateManagement;
import fr.alb.billing.model.RateManagement.TaxBreakdownItem;
import fr.alb.dto.InvoiceLineDto;
import fr.alb.dto.tax.TaxCalculationRequest;
import fr.alb.dto.tax.TaxCalculationResult;
import fr.alb.type.TaxType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain JUnit (no Quarkus) — TemplateRenderer has no CDI dependencies in its
 * render path, so we can exercise it directly without booting the app.
 */
class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    private InvoiceTemplate loadDefaultTemplate() throws IOException {
        String html = readResource("/seed/invoice-template.html");
        String css = readResource("/seed/invoice-template.css");
        InvoiceTemplate t = new InvoiceTemplate("test");
        t.template = new InvoiceTemplate.TemplateContent(html, css);
        return t;
    }

    private String readResource(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Invoice mkInvoice(String status, BigDecimal subtotal) {
        Invoice inv = new Invoice();
        inv.status = status;
        inv.facility = "Port-A";
        inv.customerName = "TestShipper";
        inv.currency = "EUR";
        inv.draftNumber = "DFT00006";
        inv.subtotalAmount = subtotal;
        inv.totalTaxAmount = BigDecimal.ZERO;
        inv.grandTotalAmount = subtotal;
        inv.lines = new ArrayList<>();
        return inv;
    }

    private InvoiceLineSnap snapLine(String desc, BigDecimal amount) {
        InvoiceLineSnap l = new InvoiceLineSnap();
        l.description = desc;
        l.quantity = BigDecimal.ONE;
        l.uom = "unit";
        l.unitPrice = amount;
        l.amount = amount;
        l.currency = "EUR";
        l.taxTotal = BigDecimal.ZERO;
        l.finalAmount = amount;
        return l;
    }

    private InvoiceLineDto dtoLine(String desc, BigDecimal amount) {
        return new InvoiceLineDto(
            "item-1", "ITM-1", desc, BigDecimal.ONE, "unit",
            amount, amount, "EUR", null, null, Collections.emptyList()
        );
    }

    @Test
    void finalInvoice_withSnapLines_rendersDescriptionAndAmount() throws IOException {
        InvoiceTemplate t = loadDefaultTemplate();
        Invoice inv = mkInvoice("FINAL", new BigDecimal("150.00"));
        inv.lines.add(snapLine("Stevedoring fee", new BigDecimal("150.00")));

        String html = renderer.render(t, inv);

        assertTrue(html.contains("Stevedoring fee"),
            "Final invoice's snap line description should render");
        assertFalse(html.contains("Aucune ligne de facturation"),
            "Empty-state banner must NOT render when there are lines");
        assertTrue(html.contains("FACTURE"));
    }

    @Test
    void draftInvoice_withDtoViewLines_rendersDescriptionAndComputedTotals() throws IOException {
        InvoiceTemplate t = loadDefaultTemplate();
        // Draft invoice — inv.lines is empty (lines aren't persisted until
        // finalisation), persisted totals are zero. The pipeline produces
        // InvoiceLineDto items that we pass as viewLines.
        Invoice inv = mkInvoice("DRAFT", BigDecimal.ZERO);
        List<InvoiceLineDto> viewLines = List.of(
            dtoLine("Container handling 20ft", new BigDecimal("80.00")),
            dtoLine("Storage 3 days", new BigDecimal("45.00"))
        );

        String html = renderer.render(t, inv, viewLines);

        assertTrue(html.contains("Container handling 20ft"),
            "Draft invoice DTO line description should render");
        assertTrue(html.contains("Storage 3 days"));
        assertFalse(html.contains("Aucune ligne de facturation"),
            "Empty-state banner must NOT render when viewLines are supplied");
        assertTrue(html.contains("PROJET DE FACTURE"), "Draft title");
        assertTrue(html.contains("BROUILLON"));

        // Totals are computed from viewLines because inv.subtotalAmount is 0.
        // 80 + 45 = 125 → "125,00 €" in fr-FR locale.
        assertTrue(html.contains("125,00"),
            "Computed draft total (80 + 45) should appear: actual html=\n" + html);
    }

    @Test
    void emptyViewLines_rendersEmptyStateBanner() throws IOException {
        InvoiceTemplate t = loadDefaultTemplate();
        Invoice inv = mkInvoice("DRAFT", BigDecimal.ZERO);

        String html = renderer.render(t, inv, Collections.emptyList());

        assertTrue(html.contains("Aucune ligne de facturation"),
            "Empty-state banner should render when viewLines is empty AND inv.lines is empty");
    }

    @Test
    void finalInvoice_persistedSubtotal_winsOverComputed() throws IOException {
        InvoiceTemplate t = loadDefaultTemplate();
        Invoice inv = mkInvoice("FINAL", new BigDecimal("999.99"));
        // Lines amounts add to 150 but the persisted subtotal is 999.99 —
        // that's what the rendered total must show (lock the snapshot).
        inv.lines.add(snapLine("Misc", new BigDecimal("150.00")));

        String html = renderer.render(t, inv);

        assertTrue(html.contains("999,99"),
            "Persisted subtotal must take precedence over the sum of lines on FINAL");
        assertFalse(html.contains("150,00 €</td>\n                        </tr>\n                    <tr>"),
            "Computed line sum should not also appear as the subtotal");
    }

    private TaxBreakdownItem breakdown(String code, String name, double rate, double base, double amount) {
        return new TaxBreakdownItem(
            "tax-" + code, code, name, new BigDecimal(rate),
            TaxType.PERCENTAGE, new BigDecimal(base), new BigDecimal(amount));
    }

    @Test
    void final_withSingleTaxOnSingleLine_rendersOneBreakdownRow() throws IOException {
        InvoiceTemplate t = loadDefaultTemplate();
        Invoice inv = mkInvoice("FINAL", new BigDecimal("120.00"));
        InvoiceLineSnap l = snapLine("Stevedoring fee", new BigDecimal("100.00"));
        l.taxTotal = new BigDecimal("20.00");
        l.taxBreakdown = List.of(breakdown("TVA20", "TVA 20 %", 20, 100, 20));
        inv.lines.add(l);
        inv.totalTaxAmount = new BigDecimal("20.00");
        inv.grandTotalAmount = new BigDecimal("120.00");

        String html = renderer.render(t, inv);

        assertTrue(html.contains("TVA20"), "tax code should appear in the breakdown row");
        assertTrue(html.contains("TVA 20 %"), "tax name should appear too");
        // 20 EUR formatted in fr-FR is "20,00 €"
        assertTrue(html.contains("20,00"), "tax amount should be formatted with the invoice currency");
        // The generic "TVA" fallback row should NOT appear when we have a breakdown.
        assertFalse(html.matches("(?s).*<td colspan=\"4\" class=\"num\">TVA</td>.*"),
            "single fallback TVA row must NOT render when breakdown exists");
    }

    @Test
    void final_withTwoTaxesAcrossTwoLines_aggregatesByCode() throws IOException {
        InvoiceTemplate t = loadDefaultTemplate();
        Invoice inv = mkInvoice("FINAL", new BigDecimal("237.50"));

        InvoiceLineSnap l1 = snapLine("Service A", new BigDecimal("100.00"));
        l1.taxBreakdown = List.of(
            breakdown("TVA20", "TVA 20 %", 20, 100, 20),
            breakdown("ECOTAXE", "Eco-taxe", 2.5, 100, 2.5));
        InvoiceLineSnap l2 = snapLine("Service B", new BigDecimal("100.00"));
        l2.taxBreakdown = List.of(
            breakdown("TVA20", "TVA 20 %", 20, 100, 20),
            breakdown("ECOTAXE", "Eco-taxe", 2.5, 100, 2.5));
        inv.lines.add(l1);
        inv.lines.add(l2);
        inv.totalTaxAmount = new BigDecimal("45.00");
        inv.grandTotalAmount = new BigDecimal("245.00");

        String html = renderer.render(t, inv);

        // TVA20 should appear once, with total 40 (20 + 20).
        assertTrue(html.contains("40,00"), "aggregated TVA20 should appear: " + html);
        // ECOTAXE should appear once, with total 5 (2.5 + 2.5).
        assertTrue(html.contains("5,00"));
        // No duplicate rows for the same code — count occurrences of "TVA20" tag.
        int countTVA20 = html.split("TVA20", -1).length - 1;
        assertEquals(1, countTVA20, "TVA20 should be grouped into a single row");
    }

    @Test
    void draft_dtoWithoutTaxConfig_fallsBackToGenericTvaRow() throws IOException {
        // DRAFT DTO with neither contractRateId nor taxes → renderer can't
        // call TaxService, so the breakdown is empty and the template
        // falls back to the single generic TVA row.
        InvoiceTemplate t = loadDefaultTemplate();
        Invoice inv = mkInvoice("DRAFT", BigDecimal.ZERO);
        List<InvoiceLineDto> viewLines = List.of(dtoLine("Storage", new BigDecimal("80.00")));

        String html = renderer.render(t, inv, viewLines);

        assertFalse(html.contains("ECOTAXE"));
        // The generic TVA fallback row should be there (totalTax is 0 here,
        // but hasAmounts is true because subtotal=80).
        assertTrue(html.contains(">TVA<"),
            "the generic TVA fallback row should render on drafts");
    }

    /**
     * Stub TaxService that ignores the request and returns whatever the test
     * pre-loaded. Lets the renderer test exercise the DRAFT-with-tax path
     * without booting Quarkus + Cosmos.
     */
    private static class StubTaxService extends TaxService {
        private TaxCalculationResult next;
        void seed(TaxCalculationResult r) { this.next = r; }
        @Override
        public TaxCalculationResult calculateTaxes(TaxCalculationRequest req) { return next; }
        @Override
        public TaxCalculationResult calculateTaxes(TaxCalculationRequest req, boolean persist) { return next; }
    }

    private InvoiceLineDto dtoLineWithTaxes(String desc, BigDecimal amount, String rateId) {
        RateManagement.RateTax rt = new RateManagement.RateTax("tax-TVA15", false);
        return new InvoiceLineDto(
            "item-1", "ITM-1", desc, BigDecimal.ONE, "unit",
            amount, amount, "EUR", "contract-1", rateId, List.of(rt)
        );
    }

    @Test
    void draft_dtoWithTaxConfig_rendersBreakdownAndTotalsLikeFinal() throws IOException {
        // Regression for "le draft affiche taxe = 0 même quand le rate a une
        // taxe attachée". The renderer must run the same TaxService.calculate
        // call buildSnapshotForFinal would run, and surface the breakdown +
        // grand total in the preview.
        InvoiceTemplate t = loadDefaultTemplate();
        Invoice inv = mkInvoice("DRAFT", BigDecimal.ZERO);
        // 24000 base × 15 % = 3600 — the same numbers the user reports
        // wanting to see in the draft, mirroring the FINAL invoice.
        List<InvoiceLineDto> viewLines = List.of(
            dtoLineWithTaxes("Storage", new BigDecimal("24000.00"), "rate-1")
        );

        StubTaxService stub = new StubTaxService();
        TaxBreakdownItem tb = new TaxBreakdownItem(
            "tax-TVA15", "TVA15", "TVA 15 %", new BigDecimal("15"),
            TaxType.PERCENTAGE, new BigDecimal("24000"), new BigDecimal("3600"));
        TaxCalculationResult result = new TaxCalculationResult();
        result.setTotalTaxAmount(new BigDecimal("3600"));
        result.setExclusiveTaxAmount(new BigDecimal("3600"));
        result.setInclusiveTaxAmount(BigDecimal.ZERO);
        result.setFinalAmount(new BigDecimal("27600"));
        result.setTaxBreakdown(List.of(tb));
        result.setCalculationId("calc-stub");
        stub.seed(result);

        TemplateRenderer r = new TemplateRenderer();
        r.taxService = stub;

        String html = r.render(t, inv, viewLines);
        // fr-FR locales emit U+202F (JDK 11+) or U+00A0 (older JDKs) as the thousands separator;
        // collapse whitespace to ASCII space so the numeric assertions stay
        // readable regardless of which whitespace flavour the JDK emits.
        String norm = html.replace((char) 0x202F, ' ').replace((char) 0x00A0, ' ').replaceAll("\\s+", " ");

        assertTrue(norm.contains("TVA15"),
            "tax code from the on-the-fly breakdown should appear on the DRAFT");
        assertTrue(norm.contains("3 600,00"),
            "tax amount (3600) must render in the breakdown row: " + norm);
        assertTrue(norm.contains("27 600,00"),
            "grand total = subtotal + exclusive tax should appear on DRAFT");
        // No fallback row when the breakdown rendered.
        assertFalse(norm.matches("(?s).*<td colspan=.4. class=.num.>TVA</td>.*"),
            "generic TVA fallback row must NOT render when DRAFT breakdown is present");
    }

    @Test
    void mixedNullFields_doNotCrashTheRender() throws IOException {
        InvoiceTemplate t = loadDefaultTemplate();
        Invoice inv = new Invoice();
        inv.status = "DRAFT";
        // facility, customerName, currency, dates, totals all null
        InvoiceLineSnap line = new InvoiceLineSnap();
        line.description = "Bare line";
        // quantity, uom, prices all null
        inv.lines = List.of(line);

        // Just shouldn't throw; the template prints empty strings where data
        // is missing thanks to the `{{#if facility}}` etc. guards.
        String html = renderer.render(t, inv);

        assertTrue(html.contains("Bare line"));
    }
}
