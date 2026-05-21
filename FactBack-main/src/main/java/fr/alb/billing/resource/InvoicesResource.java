package fr.alb.billing.resource;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import com.mongodb.client.model.Sorts;

import fr.alb.billing.dao.InvoiceDao;
import fr.alb.dto.ErrorResponse;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("invoices")
@RunOnVirtualThread
public class InvoicesResource {

    private static final Map<String, String> SORT_FIELDS = Map.of(
        "createdDate", "createdDate",
        "TotalAmount", "amount",
        "customerName", "customerName",
        "facility", "facility",
        "status", "status",
        "draftNumber", "draftNumber",
        "finalNumber", "finalNumber"
    );

    @Inject
    InvoiceDao invoiceDao;

    @GET
    public Response listInvoices(
        @QueryParam("page") Integer page,
        @QueryParam("pageSize") Integer pageSize,
        @QueryParam("sort") String sort,
        @QueryParam("status") List<String> status,
        @QueryParam("customerName") String customerName,
        @QueryParam("facility") String facility,
        @QueryParam("draftNumber") String draftNumber,
        @QueryParam("finalNumber") String finalNumber,
        @QueryParam("createdDateFrom") String createdDateFrom,
        @QueryParam("createdDateTo") String createdDateTo
,
        @QueryParam("includePayments") @jakarta.ws.rs.DefaultValue("false") boolean includePayments
    ) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null) ? 50 : pageSize;
        if (size > 200) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "pageSize must be <= 200", 400))
                    .build();
        }

        Bson sortBson = parseSort(sort);
        if (sortBson == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Invalid sort parameter", 400))
                    .build();
        }

        Date from = null;
        Date to = null;
        try {
            if (createdDateFrom != null) {
                from = Date.from(Instant.parse(createdDateFrom));
            }
            if (createdDateTo != null) {
                to = Date.from(Instant.parse(createdDateTo));
            }
        } catch (Exception e) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Invalid date format", 400))
                    .build();
        }
        if (from != null && to != null && from.after(to)) {
            return Response.status(422)
                    .entity(new ErrorResponse("INVALID_DATE_RANGE", "createdDateFrom must be before createdDateTo", 422))
                    .build();
        }

        String customerKey = customerName == null ? null : customerName.trim().toUpperCase();
        String facilityKey = facility == null ? null : facility.trim().toUpperCase();

        Document result = invoiceDao.queryInvoices(p, size, sortBson, status,
            customerKey, facilityKey, draftNumber, finalNumber, from, to);

        List<Document> items = (List<Document>) result.getOrDefault("items", List.of());
        if (includePayments && !items.isEmpty()) {
            invoiceDao.enrichWithPaymentSummary(items);
        }
        List<Document> metaList = (List<Document>) result.getOrDefault("meta", List.of());
        int totalCount = 0; double totalAmount = 0;
        if (!metaList.isEmpty()) {
            Document meta = metaList.get(0);
            Object tc = meta.get("totalCount");
            if (tc instanceof Number) totalCount = ((Number) tc).intValue();
            Object ta = meta.get("totalAmount");
            if (ta instanceof Number) totalAmount = ((Number) ta).doubleValue();
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("items", items);
        resp.put("page", p);
        resp.put("pageSize", size);
        resp.put("totalCount", totalCount);
        resp.put("aggregates", Collections.singletonMap("totalAmount", totalAmount));
        return Response.ok(resp).build();
    }

    private Bson parseSort(String sortParam) {
        String field = "createdDate";
        int dir = -1;
        if (sortParam != null && !sortParam.isBlank()) {
            String[] parts = sortParam.split(":");
            if (parts.length == 2 && SORT_FIELDS.containsKey(parts[0])) {
                field = SORT_FIELDS.get(parts[0]);
                dir = "asc".equalsIgnoreCase(parts[1]) ? 1 : "desc".equalsIgnoreCase(parts[1]) ? -1 : Integer.MIN_VALUE;
            } else {
                return null;
            }
            if (dir == Integer.MIN_VALUE) return null;
        }
        return dir == 1 ? Sorts.ascending(field) : Sorts.descending(field);
    }
}
