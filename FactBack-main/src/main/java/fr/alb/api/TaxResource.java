package fr.alb.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import fr.alb.billing.dao.TaxRepository;
import fr.alb.dto.ErrorResponse;
import fr.alb.dto.tax.TaxCalculationRequest;
import fr.alb.dto.tax.TaxCalculationResult;
import fr.alb.billing.model.Tax;
import fr.alb.billing.service.TaxService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
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
import jakarta.ws.rs.core.Response.Status;

@Path("/api/taxes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaxResource {

        @Inject
        TaxService taxService;

        @Inject
        TaxRepository taxRepository;

        @GET
        @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
        public Response list(@QueryParam("code") String code,
                        @QueryParam("activeAt") String activeAt,
                        @QueryParam("limit") Integer limit,
                        @QueryParam("offset") Integer offset) {
                try {
                        if (code != null && !code.isBlank()) {
                                return taxRepository.findByCode(code)
                                                .map(Response::ok)
                                                .orElse(Response.status(Status.NOT_FOUND))
                                                .build();
                        }
                        Instant activeInstant = null;
                        if (activeAt != null && !activeAt.isBlank()) {
                                try {
                                        activeInstant = Instant.parse(activeAt);
                                } catch (Exception e) {
                                        return error(Status.BAD_REQUEST, "INVALID_DATE", "Invalid activeAt format", Map.of("activeAt", activeAt));
                                }
                        }
                        List<Tax> taxes = activeInstant != null
                                        ? taxService.listActive(activeInstant)
                                        : taxRepository.listAll();
                        int effectiveOffset = offset != null && offset >= 0 ? offset : 0;
                        if (limit != null && limit > 0) {
                                int end = Math.min(taxes.size(), effectiveOffset + limit);
                                if (effectiveOffset > taxes.size()) {
                                        taxes = List.of();
                                } else {
                                        taxes = taxes.subList(effectiveOffset, end);
                                }
                        } else if (effectiveOffset > 0 && effectiveOffset < taxes.size()) {
                                taxes = taxes.subList(effectiveOffset, taxes.size());
                        } else if (effectiveOffset >= taxes.size()) {
                                taxes = List.of();
                        }
                        return Response.ok(taxes).build();
                } catch (Exception e) {
                        return error(Status.INTERNAL_SERVER_ERROR, "TAX_LIST_FAILED", e.getMessage(), null);
                }
        }

        @GET
        @Path("/{id}")
        @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
        public Response get(@PathParam("id") String id) {
                Tax tax = taxRepository.findById(id);
                if (tax == null) {
                        return error(Status.NOT_FOUND, "NOT_FOUND", "Tax not found", Map.of("id", id));
                }
                return Response.ok(tax).build();
        }

        @POST
        @RolesAllowed("ROLE_ADMIN")
        public Response create(@Valid Tax tax) {
                try {
                        Tax created = taxService.createTax(tax);
                        return Response.status(Status.CREATED).entity(created).build();
                } catch (IllegalArgumentException e) {
                        return error(Status.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage(), null);
                } catch (Exception e) {
                        return error(Status.INTERNAL_SERVER_ERROR, "CREATE_FAILED", e.getMessage(), null);
                }
        }

        @PUT
        @Path("/{id}")
        @RolesAllowed("ROLE_ADMIN")
        public Response update(@PathParam("id") String id, Tax tax) {
                try {
                        Tax updated = taxService.updateTax(id, tax);
                        return Response.ok(updated).build();
                } catch (IllegalArgumentException e) {
                        return error(Status.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage(), Map.of("id", id));
                } catch (Exception e) {
                        return error(Status.INTERNAL_SERVER_ERROR, "UPDATE_FAILED", e.getMessage(), Map.of("id", id));
                }
        }

        @DELETE
        @Path("/{id}")
        @RolesAllowed("ROLE_ADMIN")
        public Response delete(@PathParam("id") String id) {
                try {
                        taxService.softDelete(id);
                        return Response.noContent().build();
                } catch (Exception e) {
                        return error(Status.INTERNAL_SERVER_ERROR, "DELETE_FAILED", e.getMessage(), Map.of("id", id));
                }
        }

        @POST
        @Path("/calculate")
        @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
        public Response calculate(@Valid TaxCalculationRequest request) {
                try {
                        TaxCalculationResult result = taxService.calculateTaxes(request);
                        return Response.ok(result).build();
                } catch (IllegalArgumentException e) {
                        return error(Status.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage(), null);
                } catch (Exception e) {
                        return error(Status.INTERNAL_SERVER_ERROR, "CALCULATION_FAILED", e.getMessage(), null);
                }
        }

        private Response error(Status status, String code, String message, Map<String, Object> details) {
                return Response.status(status)
                                .entity(new ErrorResponse(code, message, status.getStatusCode(), details))
                                .build();
        }
}
