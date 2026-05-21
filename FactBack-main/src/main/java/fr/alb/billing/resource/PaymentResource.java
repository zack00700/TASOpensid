package fr.alb.billing.resource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import fr.alb.billing.dao.PaymentDao;
import fr.alb.dto.ErrorResponse;
import fr.alb.billing.model.Payment;
import fr.alb.billing.model.PaymentAllocation;
import fr.alb.type.PaymentStatus;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
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

/**
 * CRUD endpoints for Payment — recording and tracking customer payments.
 *
 * Supports allocation of payments to invoices and payment reversal.
 */
@Path("payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class PaymentResource {

    @Inject
    PaymentDao paymentDao;

    /**
     * Create a new payment record.
     */
    @POST
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN"})
    public Response createPayment(Payment payment) {
        if (payment == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Payment body is required", 400))
                    .build();
        }
        if (payment.customerId == null || payment.customerId.isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "payment.customerId is required", 400))
                    .build();
        }
        if (payment.amount == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "payment.amount is required", 400))
                    .build();
        }
        if (payment.paymentMethod == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "payment.paymentMethod is required", 400))
                    .build();
        }
        try {
            paymentDao.createPayment(payment);
            return Response.status(201).entity(payment).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * List payments with optional filters by customerId or status.
     */
    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN", "ROLE_READONLY"})
    public Response listPayments(
            @QueryParam("customerId") String customerId,
            @QueryParam("status") String status,
            @QueryParam("page") int page,
            @QueryParam("size") int size) {
        try {
            int p = Math.max(page, 1);
            int s = (size < 1 || size > 200) ? 50 : size;
            List<Payment> payments;
            if (customerId != null && !customerId.isBlank()) {
                payments = paymentDao.listByCustomer(customerId.trim(), p, s);
            } else if (status != null && !status.isBlank()) {
                PaymentStatus paymentStatus = PaymentStatus.fromValue(status);
                payments = paymentDao.listByStatus(paymentStatus, p, s);
            } else {
                payments = paymentDao.listAll(p, s);
            }
            return Response.ok(payments).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", e.getMessage(), 400))
                    .build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Get a single payment by ID.
     */
    @GET
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN", "ROLE_READONLY"})
    public Response getPayment(@PathParam("id") String id) {
        try {
            Payment payment = paymentDao.findPayment(id);
            if (payment == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Payment not found: " + id, 404))
                        .build();
            }
            return Response.ok(payment).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Update a payment record.
     */
    @PUT
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN"})
    public Response updatePayment(@PathParam("id") String id, Payment incoming) {
        try {
            Payment existing = paymentDao.findPayment(id);
            if (existing == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Payment not found: " + id, 404))
                        .build();
            }
            incoming.setId(existing.getId());
            paymentDao.updatePayment(incoming);
            return Response.ok(incoming).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Delete a payment — only allowed when status is PENDING.
     */
    @DELETE
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN"})
    public Response deletePayment(@PathParam("id") String id) {
        try {
            Payment existing = paymentDao.findPayment(id);
            if (existing == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Payment not found: " + id, 404))
                        .build();
            }
            if (existing.status != PaymentStatus.PENDING) {
                return Response.status(409)
                        .entity(new ErrorResponse("CONFLICT",
                                "Only PENDING payments can be deleted. Current status: " + existing.status.getValue(),
                                409))
                        .build();
            }
            paymentDao.deletePayment(id);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Add an allocation to an existing payment.
     * Body: { "invoiceId": "...", "invoiceNumber": "...", "amount": 1234.56 }
     * Recomputes unallocatedAmount = payment.amount - sum(allocations.allocatedAmount).
     */
    @POST
    @Path("{id}/allocate")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN"})
    public Response allocate(@PathParam("id") String id, Map<String, Object> body) {
        if (body == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Request body is required", 400))
                    .build();
        }
        String invoiceId = (String) body.get("invoiceId");
        String invoiceNumber = (String) body.get("invoiceNumber");
        Object amountObj = body.get("amount");
        if (invoiceId == null || invoiceId.isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "invoiceId is required", 400))
                    .build();
        }
        if (amountObj == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "amount is required", 400))
                    .build();
        }
        try {
            Payment payment = paymentDao.findPayment(id);
            if (payment == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Payment not found: " + id, 404))
                        .build();
            }

            BigDecimal allocAmount = new BigDecimal(amountObj.toString());

            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setInvoiceId(invoiceId.trim());
            allocation.setInvoiceNumber(invoiceNumber != null ? invoiceNumber.trim() : null);
            allocation.setAllocatedAmount(allocAmount);

            if (payment.allocations == null) {
                payment.allocations = new java.util.ArrayList<>();
            }
            payment.allocations.add(allocation);

            BigDecimal totalAllocated = payment.allocations.stream()
                    .map(a -> a.getAllocatedAmount() != null ? a.getAllocatedAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            payment.unallocatedAmount = payment.amount != null
                    ? payment.amount.subtract(totalAllocated)
                    : totalAllocated.negate();

            paymentDao.updatePayment(payment);
            return Response.ok(payment).build();
        } catch (NumberFormatException e) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "amount must be a valid number", 400))
                    .build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Reverse a payment.
     * Body: { "reason": "..." }
     * Sets status to REVERSED and records the reversal reason.
     */
    @POST
    @Path("{id}/reverse")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_INVOICE_ADMIN"})
    public Response reverse(@PathParam("id") String id, Map<String, Object> body) {
        String reason = body != null ? (String) body.get("reason") : null;
        try {
            Payment payment = paymentDao.findPayment(id);
            if (payment == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Payment not found: " + id, 404))
                        .build();
            }
            if (payment.status == PaymentStatus.REVERSED) {
                return Response.status(409)
                        .entity(new ErrorResponse("CONFLICT", "Payment is already reversed", 409))
                        .build();
            }
            payment.status = PaymentStatus.REVERSED;
            payment.reversalReason = reason;
            paymentDao.updatePayment(payment);
            return Response.ok(payment).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }
}
