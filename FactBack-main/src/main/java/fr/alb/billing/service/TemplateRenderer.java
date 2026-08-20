package fr.alb.billing.service;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.StringHelpers;
import fr.alb.billing.model.Invoice;
import fr.alb.billing.model.InvoiceLineSnap;
import fr.alb.billing.model.InvoiceTemplate;
import fr.alb.billing.model.RateManagement;
import fr.alb.billing.model.RateManagement.TaxBreakdownItem;
import fr.alb.dto.InvoiceLineDto;
import fr.alb.dto.tax.TaxCalculationRequest;
import fr.alb.dto.tax.TaxCalculationResult;
import fr.alb.type.TaxType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class TemplateRenderer {

    private static final Logger LOG = Logger.getLogger(TemplateRenderer.class);

    private final Handlebars handlebars;

    @Inject
    TaxService taxService;

    public TemplateRenderer() {
        this.handlebars = new Handlebars();
        StringHelpers.register(this.handlebars);
    }

    /**
     * Render an InvoiceTemplate against a real Invoice, returning HTML.
     *
     * <p>Convenience overload — uses {@code invoice.lines} (the persisted
     * snapshot) as the rendered lines. Suitable for FINAL invoices but will
     * produce an empty table for DRAFTs whose lines are computed on the fly.
     * Callers in the draft path should use the three-arg overload and pass
     * the lines produced by {@code InvoiceLinePipeline.buildForDraft}.
     */
    public String render(InvoiceTemplate invoiceTemplate, Invoice invoice) {
        return render(invoiceTemplate, invoice, invoice.lines);
    }

    /**
     * Render variant that takes the line list explicitly. The {@code viewLines}
     * list may contain either {@link InvoiceLineSnap} (FINAL) or
     * {@link InvoiceLineDto} (DRAFT) — the renderer normalises both into the
     * same template-facing fields ({@code description}, {@code quantity},
     * {@code uom}, {@code unitPriceFormatted}, {@code amountFormatted}, …).
     */
    public String render(InvoiceTemplate invoiceTemplate, Invoice invoice, List<?> viewLines) {
        String htmlTemplate = extractHtml(invoiceTemplate);
        String css = extractCss(invoiceTemplate);
        Map<String, Object> context = buildContext(invoice, viewLines);
        String renderedBody = renderWithHandlebars(htmlTemplate, context);
        return combineHtmlAndCss(renderedBody, css);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String extractHtml(InvoiceTemplate invoiceTemplate) {
        if (invoiceTemplate.template != null
                && invoiceTemplate.template.html != null
                && !invoiceTemplate.template.html.isBlank()) {
            return invoiceTemplate.template.html;
        }
        return "<p>Template has no content.</p>";
    }

    private String extractCss(InvoiceTemplate invoiceTemplate) {
        if (invoiceTemplate.template != null
                && invoiceTemplate.template.css != null
                && !invoiceTemplate.template.css.isBlank()) {
            return invoiceTemplate.template.css;
        }
        return "";
    }

    private String renderWithHandlebars(String htmlTemplate, Map<String, Object> context) {
        try {
            Template template = handlebars.compileInline(htmlTemplate);
            return template.apply(context);
        } catch (IOException e) {
            throw new RuntimeException("Failed to render template: " + e.getMessage(), e);
        }
    }

    /**
     * Build the Handlebars context map from an Invoice.
     *
     * Supported variables in custom templates:
     *   {{invoiceNumber}}   – final invoice number (or empty string)
     *   {{draftNumber}}     – draft invoice number (or empty string)
     *   {{issueDate}}       – creation date as ISO string (LocalDate.toString())
     *   {{status}}          – invoice status (DRAFT / FINAL / …)
     *   {{currency}}        – invoice-level currency code
     *   {{facility}}        – seller / facility name
     *   {{customerName}}    – buyer / customer name
     *   {{subtotal}}        – subtotal amount (BigDecimal → String)
     *   {{totalTax}}        – total tax amount (BigDecimal → String)
     *   {{grandTotal}}      – grand total amount (BigDecimal → String)
     *   {{lines}}           – array of line objects, each with:
     *       {{description}}
     *       {{quantity}}
     *       {{uom}}
     *       {{unitPrice}}
     *       {{amount}}
     *       {{currency}}
     *       {{taxTotal}}
     *       {{finalAmount}}
     */
    private Map<String, Object> buildContext(Invoice invoice, List<?> viewLines) {
        Map<String, Object> ctx = new HashMap<>();

        String status = invoice.status != null ? invoice.status : "";
        ctx.put("invoiceNumber", invoice.finalNumber != null ? invoice.finalNumber : "");
        ctx.put("draftNumber", invoice.draftNumber != null ? invoice.draftNumber : "");
        ctx.put("issueDate", invoice.createdDate != null ? invoice.createdDate.toString() : "");
        ctx.put("status", status);
        ctx.put("currency", invoice.currency != null ? invoice.currency : "");
        ctx.put("facility", invoice.facility != null ? invoice.facility : "");
        ctx.put("customerName", invoice.customerName != null ? invoice.customerName : "");

        ctx.put("isDraft", "DRAFT".equalsIgnoreCase(status));
        ctx.put("isFinal", "FINAL".equalsIgnoreCase(status));

        // Resolve the lines to render. For DRAFT invoices viewLines holds
        // freshly-computed InvoiceLineDto items (invoice.lines is empty until
        // finalisation); for FINAL invoices it holds the persisted snapshot.
        List<?> effectiveLines = (viewLines != null && !viewLines.isEmpty())
            ? viewLines
            : invoice.lines;

        // Drafts arrive as InvoiceLineDto with only the per-line RateTax list
        // (taxId + inclusive flag) and no pre-summed tax. Replicate exactly
        // what buildSnapshotForFinal does — call TaxService once per line —
        // so the preview shows the same numbers the FINAL invoice will lock
        // in. Snap lines already carry the breakdown; the map stays empty for
        // them. Identity-keyed so two structurally equal DTOs are still
        // counted separately.
        Map<Object, TaxCalculationResult> draftTaxByLine = computeDraftTaxes(effectiveLines, invoice);

        BigDecimal computedSubtotal = BigDecimal.ZERO;
        BigDecimal computedTax = BigDecimal.ZERO;
        List<Map<String, Object>> lines = new ArrayList<>();
        if (effectiveLines != null) {
            for (Object item : effectiveLines) {
                Map<String, Object> l = lineToMap(item, invoice.currency, draftTaxByLine.get(item));
                if (l == null) continue;
                lines.add(l);
                BigDecimal amt = lineAmount(item);
                if (amt != null) computedSubtotal = computedSubtotal.add(amt);
                BigDecimal tax = lineTaxTotal(item, draftTaxByLine.get(item));
                if (tax != null) computedTax = computedTax.add(tax);
            }
        }
        BigDecimal computedGrand = computedSubtotal.add(computedTax);
        ctx.put("lines", lines);
        ctx.put("hasLines", !lines.isEmpty());

        // For totals we prefer the persisted Invoice values when they exist
        // (FINAL invoices lock the snapshot at finalisation). For DRAFTs the
        // persisted values are zero, so we fall back to the just-computed
        // sums so the template renders the actual current draft figure.
        BigDecimal subtotal = preferPersisted(invoice.subtotalAmount, computedSubtotal);
        BigDecimal totalTax = preferPersisted(invoice.totalTaxAmount, computedTax);
        BigDecimal grandTotal = preferPersisted(invoice.grandTotalAmount, computedGrand);

        // Raw totals kept as strings for backward compatibility with existing
        // custom templates that bind {{subtotal}}, {{totalTax}}, {{grandTotal}}.
        ctx.put("subtotal", subtotal.toString());
        ctx.put("totalTax", totalTax.toString());
        ctx.put("grandTotal", grandTotal.toString());

        NumberFormat money = currencyFormatter(invoice.currency);
        ctx.put("subtotalFormatted", formatAmount(subtotal, money));
        ctx.put("totalTaxFormatted", formatAmount(totalTax, money));
        ctx.put("grandTotalFormatted", formatAmount(grandTotal, money));
        ctx.put("hasAmounts", isPositive(grandTotal) || isPositive(subtotal) || isPositive(totalTax));

        // Per-tax breakdown — aggregate TaxBreakdownItem across snap lines
        // and the on-the-fly TaxCalculationResult for draft DTO lines, group
        // by tax code (fallback taxId), sum base & amount. So drafts show
        // the same breakdown rows the FINAL invoice will lock in.
        List<Map<String, Object>> taxBreakdown = aggregateTaxBreakdown(effectiveLines, draftTaxByLine, money);
        ctx.put("taxBreakdown", taxBreakdown);
        ctx.put("hasTaxBreakdown", !taxBreakdown.isEmpty());

        return ctx;
    }

    /**
     * For every {@link InvoiceLineDto} in {@code effectiveLines} that has a
     * tax-carrying rate, run {@link TaxService#calculateTaxes} so the draft
     * preview shows the same per-line tax + breakdown the FINAL snapshot
     * would store. The map is identity-keyed so two structurally equal DTOs
     * are still counted independently; non-DTO items are skipped (snap lines
     * already carry the breakdown). Returns an empty map (never null).
     */
    private Map<Object, TaxCalculationResult> computeDraftTaxes(List<?> effectiveLines, Invoice invoice) {
        Map<Object, TaxCalculationResult> out = new IdentityHashMap<>();
        if (effectiveLines == null || effectiveLines.isEmpty() || taxService == null) {
            return out;
        }
        java.time.Instant calcInstant = invoice.createdDate != null
            ? invoice.createdDate.atStartOfDay(ZoneId.of("Europe/Paris")).toInstant()
            : java.time.Instant.now();
        for (Object item : effectiveLines) {
            if (!(item instanceof InvoiceLineDto dto)) continue;
            boolean hasTaxConfig = (dto.contractRateId() != null && !dto.contractRateId().isBlank())
                || (dto.taxes() != null && !dto.taxes().isEmpty());
            if (!hasTaxConfig) continue;
            try {
                TaxCalculationRequest req = new TaxCalculationRequest();
                req.setBaseAmount(dto.amount());
                req.setContractId(dto.contractId());
                req.setContractRateId(dto.contractRateId());
                req.setInvoiceId(invoice.id);
                req.setCurrency(dto.currency());
                req.setInclusive(dto.taxes() != null
                    && dto.taxes().stream().anyMatch(RateManagement.RateTax::isInclusive));
                req.setCalculationDate(calcInstant);
                req.setTriggeredBy("SYSTEM");
                req.setSource("INVOICE_DRAFT_PREVIEW");
                req.setCorrelationId(invoice.id);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("invoiceId", invoice.id);
                if (dto.itemId() != null) metadata.put("itemId", dto.itemId());
                req.setMetadata(metadata);
                // persist=false: rendering a draft preview is a read — it must not
                // write TaxCalculation audit rows or mutate the contract rate summary.
                out.put(item, taxService.calculateTaxes(req, false));
            } catch (RuntimeException e) {
                // A misconfigured tax shouldn't blank the whole draft preview;
                // skip this line's breakdown and let the template render the
                // amount without tax, matching the legacy behaviour.
                LOG.warnf("Skipping draft tax calc for item %s: %s", dto.itemId(), e.getMessage());
            }
        }
        return out;
    }

    /**
     * Walk every {@link InvoiceLineSnap} in {@code effectiveLines}, group
     * its {@link TaxBreakdownItem}s by {@code code} (falling back to
     * {@code taxId} when the code is missing), sum the per-line
     * {@code baseAmount} and {@code taxAmount}, and emit one map per group
     * ready for Handlebars rendering. DTO lines (drafts) are ignored —
     * they don't carry a finalised breakdown.
     */
    private List<Map<String, Object>> aggregateTaxBreakdown(List<?> effectiveLines,
                                                            Map<Object, TaxCalculationResult> draftTaxByLine,
                                                            NumberFormat money) {
        if (effectiveLines == null || effectiveLines.isEmpty()) return new ArrayList<>();
        // Preserve insertion order so taxes appear in the same order
        // they were attached to the first line that uses them.
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Object item : effectiveLines) {
            List<TaxBreakdownItem> breakdownsForLine = null;
            if (item instanceof InvoiceLineSnap snap) {
                breakdownsForLine = snap.taxBreakdown;
            } else if (item instanceof InvoiceLineDto && draftTaxByLine != null) {
                TaxCalculationResult result = draftTaxByLine.get(item);
                if (result != null) breakdownsForLine = result.getTaxBreakdown();
            }
            if (breakdownsForLine == null) continue;
            for (TaxBreakdownItem tb : breakdownsForLine) {
                String key = tb.getCode() != null && !tb.getCode().isBlank()
                    ? tb.getCode()
                    : (tb.getTaxId() != null ? tb.getTaxId() : "TAX");
                Map<String, Object> bucket = byKey.computeIfAbsent(key, k -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("code", tb.getCode() != null ? tb.getCode() : "");
                    m.put("name", tb.getName() != null ? tb.getName() : "");
                    m.put("rate", tb.getRate() != null ? tb.getRate().toString() : "0");
                    m.put("rateDisplay", rateDisplay(tb.getRate(), tb.getType()));
                    m.put("type", tb.getType() != null ? tb.getType().name() : "");
                    m.put("__baseSum", BigDecimal.ZERO);
                    m.put("__taxSum", BigDecimal.ZERO);
                    return m;
                });
                if (tb.getBaseAmount() != null) {
                    bucket.put("__baseSum", ((BigDecimal) bucket.get("__baseSum")).add(tb.getBaseAmount()));
                }
                if (tb.getTaxAmount() != null) {
                    bucket.put("__taxSum", ((BigDecimal) bucket.get("__taxSum")).add(tb.getTaxAmount()));
                }
            }
        }
        // Materialise the sums into template-facing fields.
        List<Map<String, Object>> out = new ArrayList<>(byKey.size());
        for (Map<String, Object> bucket : byKey.values()) {
            BigDecimal baseSum = (BigDecimal) bucket.remove("__baseSum");
            BigDecimal taxSum = (BigDecimal) bucket.remove("__taxSum");
            bucket.put("baseAmount", baseSum.toString());
            bucket.put("taxAmount", taxSum.toString());
            bucket.put("baseAmountFormatted", formatAmount(baseSum, money));
            bucket.put("taxAmountFormatted", formatAmount(taxSum, money));
            out.add(bucket);
        }
        return out;
    }

    private static String rateDisplay(BigDecimal rate, TaxType type) {
        if (rate == null) return "";
        if (type == TaxType.PERCENTAGE) return rate.stripTrailingZeros().toPlainString() + " %";
        return rate.toPlainString();
    }

    /**
     * Build the template-facing map for a single line item (snap or DTO).
     * For DTO lines, {@code draftTax} (when non-null) supplies the on-the-fly
     * tax computation so the preview matches the FINAL snapshot numbers.
     */
    private Map<String, Object> lineToMap(Object item, String invoiceCurrency, TaxCalculationResult draftTax) {
        if (item instanceof InvoiceLineSnap snap) {
            NumberFormat money = currencyFormatter(
                snap.currency != null ? snap.currency : invoiceCurrency);
            Map<String, Object> l = new HashMap<>();
            l.put("description", snap.description != null ? snap.description : "");
            l.put("quantity", snap.quantity != null ? snap.quantity.toString() : "");
            l.put("uom", snap.uom != null ? snap.uom : "");
            l.put("unitPrice", snap.unitPrice != null ? snap.unitPrice.toString() : "");
            l.put("amount", snap.amount != null ? snap.amount.toString() : "");
            l.put("currency", snap.currency != null ? snap.currency : "");
            l.put("taxTotal", snap.taxTotal != null ? snap.taxTotal.toString() : "0");
            l.put("finalAmount", snap.finalAmount != null ? snap.finalAmount.toString() : "");
            l.put("unitPriceFormatted", formatAmount(snap.unitPrice, money));
            l.put("amountFormatted", formatAmount(snap.amount, money));
            l.put("taxTotalFormatted", formatAmount(snap.taxTotal, money));
            l.put("finalAmountFormatted", formatAmount(snap.finalAmount, money));
            return l;
        }
        if (item instanceof InvoiceLineDto dto) {
            NumberFormat money = currencyFormatter(
                dto.currency() != null ? dto.currency() : invoiceCurrency);
            BigDecimal taxTotal = draftTax != null && draftTax.getTotalTaxAmount() != null
                ? draftTax.getTotalTaxAmount() : BigDecimal.ZERO;
            BigDecimal finalAmount = draftTax != null && draftTax.getFinalAmount() != null
                ? draftTax.getFinalAmount()
                : (dto.amount() != null ? dto.amount() : BigDecimal.ZERO);
            Map<String, Object> l = new HashMap<>();
            l.put("description", dto.description() != null ? dto.description() : "");
            l.put("quantity", dto.quantity() != null ? dto.quantity().toString() : "");
            l.put("uom", dto.uom() != null ? dto.uom() : "");
            l.put("unitPrice", dto.unitPrice() != null ? dto.unitPrice().toString() : "");
            l.put("amount", dto.amount() != null ? dto.amount().toString() : "");
            l.put("currency", dto.currency() != null ? dto.currency() : "");
            l.put("taxTotal", taxTotal.toString());
            l.put("finalAmount", finalAmount.toString());
            l.put("unitPriceFormatted", formatAmount(dto.unitPrice(), money));
            l.put("amountFormatted", formatAmount(dto.amount(), money));
            l.put("taxTotalFormatted", formatAmount(taxTotal, money));
            l.put("finalAmountFormatted", formatAmount(finalAmount, money));
            return l;
        }
        return null;
    }

    private static BigDecimal lineAmount(Object item) {
        if (item instanceof InvoiceLineSnap snap) return snap.amount;
        if (item instanceof InvoiceLineDto dto) return dto.amount();
        return null;
    }

    private static BigDecimal lineTaxTotal(Object item, TaxCalculationResult draftTax) {
        if (item instanceof InvoiceLineSnap snap) return snap.taxTotal;
        // DTOs don't carry a pre-summed tax on the record itself; the draft
        // tax map supplies the freshly-computed total when available.
        if (draftTax != null) return draftTax.getTotalTaxAmount();
        return null;
    }

    private static BigDecimal preferPersisted(BigDecimal persisted, BigDecimal computed) {
        return (persisted != null && persisted.signum() > 0) ? persisted : computed;
    }

    private static NumberFormat currencyFormatter(String currencyCode) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.FRANCE);
        if (currencyCode != null && !currencyCode.isBlank()) {
            try {
                nf.setCurrency(Currency.getInstance(currencyCode));
            } catch (IllegalArgumentException ignored) {
                // Fallback to locale default if the invoice carries a non-ISO code.
            }
        }
        return nf;
    }

    private static String formatAmount(BigDecimal amount, NumberFormat nf) {
        if (amount == null) return "";
        return nf.format(amount);
    }

    private static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    /**
     * Wrap rendered body HTML with a full HTML document including the template CSS.
     * If the body already contains a full HTML document, return it as-is.
     */
    private String combineHtmlAndCss(String html, String css) {
        if (html.contains("<html") || html.contains("<!DOCTYPE")) {
            return html;
        }

        StringBuilder combined = new StringBuilder();
        combined.append("<!DOCTYPE html>\n");
        combined.append("<html>\n<head>\n");
        combined.append("<meta charset=\"UTF-8\">\n");
        if (css != null && !css.isBlank()) {
            combined.append("<style>\n").append(css).append("\n</style>\n");
        }
        combined.append("</head>\n<body>\n");
        combined.append(html);
        combined.append("\n</body>\n</html>");
        return combined.toString();
    }

    // -------------------------------------------------------------------------
    // Compatibility overload — kept for callers that still pass a raw Map.
    // The preview endpoint in InvoiceTemplateResource uses a sample Invoice now,
    // but other legacy callers may still use this method.
    // -------------------------------------------------------------------------

    /**
     * @deprecated Prefer {@link #render(InvoiceTemplate, Invoice)}.
     *             This overload performs simple {{key}} substitution only.
     */
    @Deprecated
    public String render(InvoiceTemplate template, Map<String, Object> data) {
        if (template == null || template.template == null) {
            throw new IllegalArgumentException("Template or template content cannot be null");
        }
        String htmlTemplate = extractHtml(template);
        String css = extractCss(template);
        String rendered = renderWithHandlebars(htmlTemplate, data);
        return combineHtmlAndCss(rendered, css);
    }
}
