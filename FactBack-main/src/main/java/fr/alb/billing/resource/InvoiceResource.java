package fr.alb.billing.resource;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fr.alb.billing.dao.InvoiceDao;
import fr.alb.billing.dao.InvoiceDaoImpl;
import fr.alb.dao.ItemDao;
import fr.alb.dto.ErrorResponse;
import fr.alb.bol.model.BillOfLading;
import fr.alb.billing.model.Invoice;
import fr.alb.billing.model.InvoiceTemplate;
import fr.alb.yard.model.Item;
import fr.alb.billing.service.TemplateRenderer;
import fr.alb.billing.event.InvoiceFinalized;
import fr.alb.platform.event.DomainEventPublisher;
import org.bson.Document;
import org.jboss.logging.Logger;
import io.quarkus.qute.Template;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/invoice") // align with FE URLs
@RunOnVirtualThread
public class InvoiceResource {

    private static final Logger LOG = Logger.getLogger(InvoiceResource.class);

    @Inject InvoiceDao invoiceDao;
    @Inject ItemDao itemDao;
    @Inject Template invoice; // templates/invoice.html
    @Inject fr.alb.billing.service.InvoiceLinePipeline invoiceLinePipeline;
    @Inject fr.alb.billing.service.InvoiceNumberService invoiceNumberService;
    @Inject SecurityIdentity securityIdentity;
    @Inject TemplateRenderer templateRenderer;
    @Inject DomainEventPublisher domainEvents;

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("%+$", "");
    }

    @POST
    @Path("{invoicesId}/draft/customer/{customerName}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN"})
    public Response generateDraftInvoices(@PathParam("invoicesId") List<String> idsInvoice,
                                          @PathParam("customerName") String customer) {
        try {
            var invoiceOpt = invoiceDao.makeInvoice(idsInvoice, customer, null);
            if (invoiceOpt.isPresent()) {
                Invoice inv = invoiceOpt.get();
                Map<String, String> body = Map.of(
                        "invoiceId", inv.id,
                        "previewUrl", "/api/invoice/" + inv.id + "/html"
                );
                return Response.status(201).entity(body).build();
            }
            return Response.status(422)
                    .entity(new ErrorResponse("NO_BILLABLE_LINES",
                            "Aucune ligne facturable trouvée pour les items sélectionnés. Vérifiez que des événements actifs et des contrats existent.", 422))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(409)
                    .entity(new ErrorResponse("CONFLICT", e.getMessage(), 409))
                    .build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @POST
    @Path("bl/{blId}/draft/customer/{customerName}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN"})
    public Response generateDraftInvoiceByBillOfLading(@PathParam("blId") String blIdRaw,
                                                       @PathParam("customerName") String customer) {
        try {
            final String blId = clean(blIdRaw);

            // BillOfLading must have String @BsonId id for this to work:
            BillOfLading billOfLading = BillOfLading.findById(blId);
            if (billOfLading == null || billOfLading.getItemIds() == null || billOfLading.getItemIds().isEmpty()) {
                return Response.status(404).build();
            }

            List<String> itemIds = billOfLading.getItemIds();
            var invoiceOpt = invoiceDao.makeInvoice(itemIds, customer, blId);
            if (invoiceOpt.isPresent()) {
                Invoice inv = invoiceOpt.get();
                Map<String, String> body = Map.of(
                        "invoiceId", inv.id,
                        "previewUrl", "/api/invoice/" + inv.id + "/html"
                );
                return Response.status(201).entity(body).build();
            }
            return Response.status(422)
                    .entity(new ErrorResponse("NO_BILLABLE_LINES",
                            "Aucune ligne facturable trouvée pour ce connaissement. Vérifiez que des événements actifs et des contrats existent.", 422))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(409)
                    .entity(new ErrorResponse("CONFLICT", e.getMessage(), 409))
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate draft invoice for BL %s, customer %s", blIdRaw, customer);
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * @deprecated Use GET /invoices (InvoicesResource) which supports pagination, filtering and sorting.
     *             This endpoint is capped at 500 records to prevent OOM on large datasets.
     */
    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response getInvoices() {
        try {
            List<Invoice> invoices = Invoice.find("{}").page(io.quarkus.panache.common.Page.ofSize(500)).list();
            if (invoices.size() == 500) {
                LOG.warn("GET /invoice returned the 500-record cap. Use GET /invoices for paginated access.");
            }
            return Response.status(200).entity(invoices).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @GET
    @Path("vessel/{vesselName}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response getInvoicesByVessel(@PathParam("vesselName") String vesselName,
                                        @QueryParam("start") String start,
                                        @QueryParam("end") String end) {
        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);
            List<Document> result = invoiceDao.getInvoicesByVessel(vesselName, startDate, endDate);
            return Response.status(200).entity(result).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @PUT
    @Path("{invoiceId}/finalize")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN"})
    public Response finalizeDraftInvoice(@PathParam("invoiceId") String invoiceIdRaw) {
        final String invoiceId = clean(invoiceIdRaw);

        try {
            Invoice inv = Invoice.findById(invoiceId);
            if (inv == null) return Response.status(404).build();
            if ("FINAL".equalsIgnoreCase(inv.status)) {
                return Response.status(409)
                        .entity(new ErrorResponse("CONFLICT", "Already final", 409))
                        .build();
            }

            var result = invoiceLinePipeline.buildSnapshotForFinal(inv);
            var snaps = result.lines();
            if (snaps.isEmpty()) {
                return Response.status(409)
                        .entity(new ErrorResponse("CONFLICT", "No eligible items/events to invoice", 409))
                        .build();
            }
            String currency = snaps.get(0).currency != null ? snaps.get(0).currency : "EUR";

            inv.lines = snaps;
            inv.currency = currency;
            inv.subtotalAmount = result.subtotal();
            inv.inclusiveTaxTotal = result.inclusiveTaxTotal();
            inv.exclusiveTaxTotal = result.exclusiveTaxTotal();
            inv.totalTaxAmount = result.totalTax();
            inv.grandTotalAmount = result.grandTotal();
            inv.taxBreakdown = result.taxBreakdown();
            inv.taxCalculationIds = result.taxCalculationIds();
            inv.amount = result.grandTotal().doubleValue();
            inv.finalNumber = invoiceNumberService.generateFinalNumber();
            inv.status = "FINAL";

            // Re-bind to the active 'final' InvoiceTemplate (different design from draft template).
            // Cosmos rejects $sort on non-indexed fields; backend invariant ensures at most one active per type.
            InvoiceTemplate finalTemplate = InvoiceTemplate
                    .<InvoiceTemplate>find("status = ?1 and type = ?2", "active", "final")
                    .firstResult();
            if (finalTemplate != null) {
                inv.templateId = finalTemplate.id;
            }
            // If no active 'final' template exists, leave inv.templateId as-is (the draft binding).
            // getInvoiceHtml's fallback chain will handle rendering.

            inv.update();

            LOG.debugf("[InvoiceFinalize] id=%s items=%d lines=%d total=%.2f scope=%s",
                    inv.id, result.itemCount(), snaps.size(), result.grandTotal().doubleValue(), result.scopeTag());

            domainEvents.publish(new InvoiceFinalized(
                    String.valueOf(inv.id),
                    inv.finalNumber,
                    inv.customerKey,
                    inv.customerName,
                    inv.grandTotalAmount,
                    inv.currency,
                    java.time.Instant.now()));

            return Response.ok(inv).build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to finalize invoice %s", invoiceId);
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), 500))
                    .build();
        }
    }

    @GET
    @Path("{invoiceId}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response getInvoice(@PathParam("invoiceId") String invoiceIdRaw) {
        final String invoiceId = clean(invoiceIdRaw);
        Invoice inv = Invoice.findById(invoiceId);
        if (inv == null) return Response.status(404).build();
        return Response.ok(inv).build();
    }

    @DELETE
    @Path("{invoiceId}")
    @RolesAllowed("ROLE_ADMIN")
    public Response deleteInvoice(@PathParam("invoiceId") String invoiceIdRaw) {
        final String invoiceId = clean(invoiceIdRaw);

        long deleted = Invoice.delete("_id = ?1 and status = 'DRAFT'", invoiceId);
        if (deleted == 1) {
            List<Item> items = Item.list("relatedInvoice", invoiceId);
            for (Item it : items) {
                it.setRelatedInvoice(null);
                it.update();
            }
            LOG.infof("[InvoiceDelete] id=%s user=%s unlinked=%d", invoiceId,
                    securityIdentity.getPrincipal().getName(), items.size());
            return Response.noContent().build();
        }

        Invoice inv = Invoice.findById(invoiceId);
        if (inv == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "Invoice not found", 404))
                    .build();
        }
        return Response.status(409)
                .entity(new ErrorResponse("CONFLICT", "Only draft invoices can be deleted", 409))
                .build();
    }

    @GET
    @Path("{invoiceId}/html")
    @Produces("text/html; charset=UTF-8")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response getInvoiceHtml(@PathParam("invoiceId") String invoiceIdRaw) {
        final String invoiceId = clean(invoiceIdRaw);
        Invoice inv = Invoice.findById(invoiceId);
        if (inv == null) return Response.status(404).build();

        java.util.List<?> viewLines;
        java.util.List<fr.alb.dto.CurrencyTotalDto> totals;

        if ("FINAL".equalsIgnoreCase(inv.status) && inv.lines != null && !inv.lines.isEmpty()) {
            viewLines = inv.lines;
            java.util.Map<String, java.math.BigDecimal> map = inv.lines.stream()
                    .collect(java.util.stream.Collectors.groupingBy(l -> l.currency,
                            java.util.stream.Collectors.reducing(java.math.BigDecimal.ZERO, l -> l.amount,
                                    java.math.BigDecimal::add)));
            totals = map.entrySet().stream()
                    .map(e -> new fr.alb.dto.CurrencyTotalDto(e.getKey(), e.getValue()))
                    .sorted(java.util.Comparator.comparing(fr.alb.dto.CurrencyTotalDto::currency))
                    .toList();
        } else {
            var compRes = invoiceLinePipeline.buildForDraft(inv);
            var dtos = compRes.lines();
            viewLines = dtos;
            totals = invoiceLinePipeline.totalsByCurrency(dtos);
        }

        // --- Custom Handlebars template (when invoice.templateId is set, OR fallback to active template matching inv.status) ---
        InvoiceTemplate customTemplate = null;
        if (inv.templateId != null && !inv.templateId.isBlank()) {
            customTemplate = InvoiceTemplate.findById(inv.templateId);
            if (customTemplate == null) {
                LOG.warnf("[InvoiceHtml] Custom template %s not found for invoice %s, will try active fallback",
                        inv.templateId, invoiceId);
            }
        }
        if (customTemplate == null) {
            // Fallback: invoice has no templateId (legacy) or its template was deleted.
            // Look up the active template matching the invoice's status. DRAFT → type='draft', otherwise → type='final'.
            // No sort: Cosmos rejects $sort on non-indexed fields, and the backend enforces "at most one active per type".
            String wantType = "DRAFT".equalsIgnoreCase(inv.status) ? "draft" : "final";
            customTemplate = InvoiceTemplate
                    .<InvoiceTemplate>find("status = ?1 and type = ?2", "active", wantType)
                    .firstResult();
            if (customTemplate == null) {
                // Last-resort: any active template of any type (covers prod data without `type` migrated yet).
                customTemplate = InvoiceTemplate
                        .<InvoiceTemplate>find("status = ?1", "active")
                        .firstResult();
            }
        }
        if (customTemplate != null) {
            try {
                String customHtml = templateRenderer.render(customTemplate, inv);
                return Response.ok(customHtml)
                        .type("text/html; charset=UTF-8")
                        .header("Cache-Control", "no-store")
                        .build();
            } catch (Exception e) {
                LOG.warnf("[InvoiceHtml] Custom template %s failed for invoice %s, falling back to Qute: %s",
                        customTemplate.id, invoiceId, e.getMessage());
            }
        }

        // --- Fallback: existing Qute-based rendering ---
        Locale locale = Locale.FRANCE;
        java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance(locale);

        String defaultCurrency = inv.currency != null ? inv.currency : "EUR";
        String primaryCurrency = totals.isEmpty() ? defaultCurrency : totals.get(0).currency();
        nf.setCurrency(java.util.Currency.getInstance(primaryCurrency));
        java.math.BigDecimal primaryTotal = totals.stream()
                .filter(t -> t.currency().equals(primaryCurrency))
                .map(fr.alb.dto.CurrencyTotalDto::total)
                .findFirst().orElse(java.math.BigDecimal.ZERO);

        String issueDate = inv.createdDate == null ? "" :
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", locale).format(inv.createdDate);

        String html = invoice
                .data("invoiceNumber", inv.finalNumber != null ? inv.finalNumber : inv.draftNumber)
                .data("issueDate", issueDate)
                .data("status", inv.status)
                .data("seller", inv.facility)
                .data("buyer", inv.customerName)
                .data("lines", viewLines)
                .data("grandTotal", nf.format(primaryTotal))
                .data("currency", primaryCurrency)
                .data("totals", totals)
                .data("notes", "")
                .render();

        return Response.ok(html)
                .type("text/html; charset=UTF-8")
                .header("Cache-Control", "no-store")
                .build();
    }

    @PUT
    @Path("{id}/repair-link")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN"})
    public Response repairLink(@PathParam("id") String idRaw) {
        final String id = clean(idRaw);
        Invoice inv = Invoice.findById(id);
        if (inv == null || inv.billOfLadingId == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Missing invoice or billOfLadingId", 400))
                    .build();
        }
        List<Item> blItems = Item.list("billOfLadingId", inv.billOfLadingId);
        for (Item it : blItems) {
            it.setRelatedInvoice(inv.id);
            it.update();
        }
        return Response.ok(Map.of("linked", blItems.size())).build();
    }

    @GET
    @Path("container-count")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response getContainerCount(@QueryParam("container_type") String containerType,
                                      @QueryParam("start_date") String startDate,
                                      @QueryParam("end_date") String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            long count = itemDao.getContainerCount(containerType, start, end);
            return Response.ok(Collections.singletonMap("count", count)).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

}
