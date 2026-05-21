package fr.alb.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import fr.alb.billing.dao.TaxCalculationRepository;
import fr.alb.dto.ErrorResponse;
import fr.alb.billing.model.TaxCalculation;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Path("/api/tax-calculations")
@Produces(MediaType.APPLICATION_JSON)
public class TaxCalculationResource {

        private static final int DEFAULT_LIMIT = 50;

        @Inject
        TaxCalculationRepository taxCalculationRepository;

        @GET
        @Path("/by-contract/{contractId}")
        public Response byContract(@PathParam("contractId") String contractId,
                        @QueryParam("limit") Integer limit,
                        @QueryParam("offset") Integer offset,
                        @QueryParam("dateFrom") String dateFrom,
                        @QueryParam("dateTo") String dateTo) {
                return queryCalculations(() -> taxCalculationRepository.findByContractId(contractId, effectiveLimit(limit), effectiveOffset(offset)),
                                dateFrom, dateTo, Map.of("contractId", contractId));
        }

        @GET
        @Path("/by-rate/{rateId}")
        public Response byRate(@PathParam("rateId") String rateId,
                        @QueryParam("limit") Integer limit,
                        @QueryParam("offset") Integer offset,
                        @QueryParam("dateFrom") String dateFrom,
                        @QueryParam("dateTo") String dateTo) {
                return queryCalculations(() -> taxCalculationRepository.findByRateId(rateId, effectiveLimit(limit), effectiveOffset(offset)),
                                dateFrom, dateTo, Map.of("rateId", rateId));
        }

        @GET
        @Path("/by-invoice/{invoiceId}")
        public Response byInvoice(@PathParam("invoiceId") String invoiceId,
                        @QueryParam("limit") Integer limit,
                        @QueryParam("offset") Integer offset,
                        @QueryParam("dateFrom") String dateFrom,
                        @QueryParam("dateTo") String dateTo) {
                return queryCalculations(() -> taxCalculationRepository.findByInvoiceId(invoiceId, effectiveLimit(limit), effectiveOffset(offset)),
                                dateFrom, dateTo, Map.of("invoiceId", invoiceId));
        }

        private Response queryCalculations(SupplierWithException<List<TaxCalculation>> supplier,
                        String dateFrom, String dateTo, Map<String, Object> errorDetails) {
                try {
                        Instant from = parseInstant(dateFrom);
                        Instant to = parseInstant(dateTo);
                        List<TaxCalculation> results = supplier.get();
                        if (from != null || to != null) {
                                results = filterByDate(results, from, to);
                        }
                        return Response.ok(results).build();
                } catch (IllegalArgumentException e) {
                        return error(Status.BAD_REQUEST, "INVALID_DATE", e.getMessage(), errorDetails);
                } catch (Exception e) {
                        return error(Status.INTERNAL_SERVER_ERROR, "QUERY_FAILED", e.getMessage(), errorDetails);
                }
        }

        private List<TaxCalculation> filterByDate(List<TaxCalculation> list, Instant from, Instant to) {
                return list.stream()
                                .filter(calc -> {
                                        Instant date = calc.getCalculationDate();
                                        if (date == null) {
                                                return true;
                                        }
                                        boolean afterFrom = from == null || !date.isBefore(from);
                                        boolean beforeTo = to == null || !date.isAfter(to);
                                        return afterFrom && beforeTo;
                                })
                                .collect(Collectors.toList());
        }

        private Instant parseInstant(String value) {
                if (value == null || value.isBlank()) {
                        return null;
                }
                try {
                        return Instant.parse(value);
                } catch (Exception e) {
                        throw new IllegalArgumentException("Invalid instant: " + value);
                }
        }

        private int effectiveLimit(Integer limit) {
                return limit != null && limit > 0 ? limit : DEFAULT_LIMIT;
        }

        private int effectiveOffset(Integer offset) {
                return offset != null && offset >= 0 ? offset : 0;
        }

        private Response error(Status status, String code, String message, Map<String, Object> details) {
                return Response.status(status)
                                .entity(new ErrorResponse(code, message, status.getStatusCode(), details))
                                .build();
        }

        @FunctionalInterface
        private interface SupplierWithException<T> {
                T get() throws Exception;
        }
}
