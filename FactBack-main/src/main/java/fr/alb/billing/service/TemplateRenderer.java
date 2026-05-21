package fr.alb.billing.service;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.StringHelpers;
import fr.alb.billing.model.Invoice;
import fr.alb.billing.model.InvoiceLineSnap;
import fr.alb.billing.model.InvoiceTemplate;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TemplateRenderer {

    private final Handlebars handlebars;

    public TemplateRenderer() {
        this.handlebars = new Handlebars();
        StringHelpers.register(this.handlebars);
    }

    /**
     * Render an InvoiceTemplate against a real Invoice, returning HTML.
     */
    public String render(InvoiceTemplate invoiceTemplate, Invoice invoice) {
        String htmlTemplate = extractHtml(invoiceTemplate);
        String css = extractCss(invoiceTemplate);
        Map<String, Object> context = buildContext(invoice);
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
    private Map<String, Object> buildContext(Invoice invoice) {
        Map<String, Object> ctx = new HashMap<>();

        ctx.put("invoiceNumber", invoice.finalNumber != null ? invoice.finalNumber : "");
        ctx.put("draftNumber", invoice.draftNumber != null ? invoice.draftNumber : "");
        ctx.put("issueDate", invoice.createdDate != null ? invoice.createdDate.toString() : "");
        ctx.put("status", invoice.status != null ? invoice.status : "");
        ctx.put("currency", invoice.currency != null ? invoice.currency : "");
        ctx.put("facility", invoice.facility != null ? invoice.facility : "");
        ctx.put("customerName", invoice.customerName != null ? invoice.customerName : "");

        // Totals (Invoice has subtotalAmount, totalTaxAmount, grandTotalAmount as BigDecimal)
        ctx.put("subtotal", invoice.subtotalAmount != null ? invoice.subtotalAmount.toString() : "0");
        ctx.put("totalTax", invoice.totalTaxAmount != null ? invoice.totalTaxAmount.toString() : "0");
        ctx.put("grandTotal", invoice.grandTotalAmount != null ? invoice.grandTotalAmount.toString() : "0");

        // Lines (InvoiceLineSnap fields: description, quantity, uom, unitPrice, amount,
        //        currency, taxTotal, finalAmount)
        // Note: InvoiceLineSnap has no taxRate field — omitted.
        if (invoice.lines != null) {
            List<Map<String, Object>> lines = new ArrayList<>();
            for (InvoiceLineSnap line : invoice.lines) {
                Map<String, Object> l = new HashMap<>();
                l.put("description", line.description != null ? line.description : "");
                l.put("quantity", line.quantity != null ? line.quantity.toString() : "");
                l.put("uom", line.uom != null ? line.uom : "");
                l.put("unitPrice", line.unitPrice != null ? line.unitPrice.toString() : "");
                l.put("amount", line.amount != null ? line.amount.toString() : "");
                l.put("currency", line.currency != null ? line.currency : "");
                l.put("taxTotal", line.taxTotal != null ? line.taxTotal.toString() : "0");
                l.put("finalAmount", line.finalAmount != null ? line.finalAmount.toString() : "");
                lines.add(l);
            }
            ctx.put("lines", lines);
        } else {
            ctx.put("lines", java.util.Collections.emptyList());
        }

        return ctx;
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
