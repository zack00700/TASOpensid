package fr.alb.ai.featurerequest.resource;

import fr.alb.ai.featurerequest.service.FeatureRequestAiService;
import fr.alb.dto.ErrorResponse;
import fr.alb.ai.featurerequest.model.FeatureRequest;
import fr.alb.ai.featurerequest.model.InternalComment;
import fr.alb.ai.featurerequest.model.StatusChange;
import fr.alb.sequence.service.TicketSequenceService;
import fr.alb.type.FeatureRequestStatus;
import fr.alb.type.TicketCategory;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Feature Request endpoints with AI-driven clarification via Claude.
 *
 * Lifecycle:
 *   POST /feature-requests          → creates request, triggers first AI question (CLARIFYING)
 *   POST /feature-requests/{id}/chat → user replies; AI continues until CLARIFICATION_COMPLETE
 *   PATCH /feature-requests/{id}/status → admin approves/rejects/progresses
 *   GET  /feature-requests/backlog  → admin view of actionable backlog
 */
@Path("feature-requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class FeatureRequestResource {

    @Inject
    FeatureRequestAiService aiService;

    @Inject
    TicketSequenceService ticketSequenceService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /feature-requests
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Create a new feature request and immediately start the AI clarification.
     * The AI receives the description as the first user message and replies with
     * its opening questions.
     */
    @POST
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_EDI", "ROLE_READONLY"})
    public Response create(Map<String, Object> body, @Context SecurityContext securityContext) {
        try {
            String title = body == null ? null : (String) body.get("title");
            String description = body == null ? null : (String) body.get("description");
            if (title == null || title.isBlank()) {
                return Response.status(400)
                        .entity(new ErrorResponse("BAD_REQUEST", "title is required", 400))
                        .build();
            }
            if (description == null || description.isBlank()) {
                return Response.status(400)
                        .entity(new ErrorResponse("BAD_REQUEST", "description is required", 400))
                        .build();
            }

            String principal = resolvePrincipal(securityContext);

            FeatureRequest request = new FeatureRequest();
            request.title = title;
            request.description = description;
            request.createdBy = principal;
            request.createdAt = Instant.now();
            request.status = FeatureRequestStatus.DRAFT;

            // Ticket number
            request.ticketNumber = ticketSequenceService.nextTicketNumber();

            // Optional category
            if (body.containsKey("category")) {
                Object cat = body.get("category");
                if (cat != null) {
                    request.category = TicketCategory.fromValue(cat.toString());
                }
            }

            // Ensure collections are initialised
            request.statusHistory = new ArrayList<>();
            request.internalComments = new ArrayList<>();

            // Initial status history entry
            StatusChange initial = new StatusChange();
            initial.fromStatus = null;
            initial.toStatus = FeatureRequestStatus.DRAFT.getValue();
            initial.changedBy = principal;
            initial.changedAt = Instant.now();
            request.statusHistory.add(initial);

            // Kick off AI clarification with the description as the first user message
            aiService.processUserMessage(request, description);

            request.persist();
            return Response.status(201).entity(request).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /feature-requests
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * List feature requests. Admins see all; other roles see only their own.
     */
    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_EDI", "ROLE_READONLY"})
    public Response list(
            @QueryParam("status") String status,
            @QueryParam("page") int page,
            @QueryParam("size") int size,
            @Context SecurityContext securityContext) {
        try {
            int p = Math.max(page, 1);
            int s = (size < 1 || size > 200) ? 50 : size;
            String principal = resolvePrincipal(securityContext);
            boolean isAdmin = securityContext.isUserInRole("ROLE_ADMIN");

            List<FeatureRequest> requests;
            if (status != null && !status.isBlank()) {
                FeatureRequestStatus fs = FeatureRequestStatus.fromValue(status);
                if (isAdmin) {
                    requests = FeatureRequest.find("status", fs)
                            .page(Page.of(p - 1, s)).list();
                } else {
                    requests = FeatureRequest.find("status = ?1 and createdBy = ?2", fs, principal)
                            .page(Page.of(p - 1, s)).list();
                }
            } else {
                if (isAdmin) {
                    requests = FeatureRequest.find("{}").page(Page.of(p - 1, s)).list();
                } else {
                    requests = FeatureRequest.find("createdBy", principal)
                            .page(Page.of(p - 1, s)).list();
                }
            }
            return Response.ok(requests).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /feature-requests/backlog
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Admin backlog view: requests that are actionable (READY_FOR_REVIEW, APPROVED, IN_PROGRESS),
     * ordered by priority descending.
     */
    @GET
    @Path("backlog")
    @RolesAllowed({"ROLE_ADMIN"})
    public Response backlog() {
        try {
            List<FeatureRequest> results = FeatureRequest
                    .find("status in ?1",
                            Sort.descending("priority"),
                            List.of(
                                    FeatureRequestStatus.READY_FOR_REVIEW,
                                    FeatureRequestStatus.APPROVED,
                                    FeatureRequestStatus.IN_PROGRESS))
                    .list();
            return Response.ok(results).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /feature-requests/milestones
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Admin: return distinct milestone values across all feature requests.
     */
    @GET
    @Path("milestones")
    @RolesAllowed({"ROLE_ADMIN"})
    public Response milestones() {
        try {
            List<String> results = FeatureRequest.mongoCollection()
                    .distinct("milestone", String.class)
                    .into(new ArrayList<>());
            return Response.ok(results).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /feature-requests/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_EDI", "ROLE_READONLY"})
    public Response getById(@PathParam("id") String id, @Context SecurityContext securityContext) {
        FeatureRequest request = FeatureRequest.findById(id);
        if (request == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "Feature request not found: " + id, 404))
                    .build();
        }
        String principal = resolvePrincipal(securityContext);
        boolean isAdmin = securityContext.isUserInRole("ROLE_ADMIN");
        if (!isAdmin && !principal.equals(request.createdBy)) {
            return Response.status(403)
                    .entity(new ErrorResponse("FORBIDDEN", "Access denied", 403))
                    .build();
        }
        return Response.ok(request).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /feature-requests/{id}/chat
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Send a clarification reply. Returns 400 if clarification is already done.
     */
    @POST
    @Path("{id}/chat")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_EDI", "ROLE_READONLY"})
    public Response chat(@PathParam("id") String id, Map<String, String> body,
                         @Context SecurityContext securityContext) {
        try {
            FeatureRequest request = FeatureRequest.findById(id);
            if (request == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Feature request not found: " + id, 404))
                        .build();
            }

            String principal = resolvePrincipal(securityContext);
            boolean isAdmin = securityContext.isUserInRole("ROLE_ADMIN");
            if (!isAdmin && !principal.equals(request.createdBy)) {
                return Response.status(403)
                        .entity(new ErrorResponse("FORBIDDEN", "Access denied", 403))
                        .build();
            }

            if (request.clarificationsDone) {
                return Response.status(400)
                        .entity(new ErrorResponse("BAD_REQUEST",
                                "Clarification is already complete for this request", 400))
                        .build();
            }

            String message = body == null ? null : body.get("message");
            if (message == null || message.isBlank()) {
                return Response.status(400)
                        .entity(new ErrorResponse("BAD_REQUEST", "message is required", 400))
                        .build();
            }

            aiService.processUserMessage(request, message);
            request.updatedAt = Instant.now();
            request.updatedBy = principal;
            request.update();
            return Response.ok(request).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /feature-requests/{id}/status
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Admin-only: update status and associated metadata.
     * Body accepts: status, reason, priority, estimatedEffort, assignedTo, milestone, dueDate.
     */
    @PATCH
    @Path("{id}/status")
    @RolesAllowed({"ROLE_ADMIN"})
    public Response updateStatus(@PathParam("id") String id, Map<String, Object> body,
                                 @Context SecurityContext securityContext) {
        try {
            FeatureRequest request = FeatureRequest.findById(id);
            if (request == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Feature request not found: " + id, 404))
                        .build();
            }

            String principal = resolvePrincipal(securityContext);
            if (request.statusHistory == null) request.statusHistory = new ArrayList<>();

            if (body.containsKey("status")) {
                FeatureRequestStatus newStatus = FeatureRequestStatus.fromValue(body.get("status").toString());

                // Build audit entry
                StatusChange change = new StatusChange();
                change.fromStatus = request.status != null ? request.status.getValue() : null;
                change.toStatus = newStatus.getValue();
                change.changedBy = principal;
                change.changedAt = Instant.now();
                if (body.containsKey("reason")) {
                    change.reason = body.get("reason").toString();
                }
                request.statusHistory.add(change);

                request.status = newStatus;
                if (newStatus == FeatureRequestStatus.APPROVED) {
                    request.approvedBy = principal;
                    request.approvedAt = Instant.now();
                }
                if (newStatus == FeatureRequestStatus.REJECTED && body.containsKey("reason")) {
                    request.rejectedReason = body.get("reason").toString();
                }
            }

            if (body.containsKey("reason") && request.status != FeatureRequestStatus.REJECTED) {
                // reason without a status change is still stored on the last history entry (already done above)
                // but also keep rejectedReason in sync if status is already REJECTED
            }
            if (body.containsKey("priority")) {
                Object prio = body.get("priority");
                if (prio instanceof Number) {
                    request.priority = ((Number) prio).intValue();
                }
            }
            if (body.containsKey("estimatedEffort")) {
                request.estimatedEffort = body.get("estimatedEffort").toString();
            }
            if (body.containsKey("assignedTo")) {
                request.assignedTo = body.get("assignedTo").toString();
            }
            if (body.containsKey("milestone")) {
                request.milestone = body.get("milestone").toString();
            }
            if (body.containsKey("dueDate")) {
                try {
                    request.dueDate = Instant.parse(body.get("dueDate").toString());
                } catch (Exception ignored) {
                    // silently ignore unparseable dueDate
                }
            }

            request.updatedAt = Instant.now();
            request.updatedBy = principal;
            request.update();
            return Response.ok(request).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /feature-requests/{id}/comments
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Admin-only: add an internal comment to a feature request.
     */
    @POST
    @Path("{id}/comments")
    @RolesAllowed({"ROLE_ADMIN"})
    public Response addComment(@PathParam("id") String id, Map<String, String> body,
                               @Context SecurityContext securityContext) {
        try {
            FeatureRequest request = FeatureRequest.findById(id);
            if (request == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Feature request not found: " + id, 404))
                        .build();
            }

            String content = body == null ? null : body.get("content");
            if (content == null || content.isBlank()) {
                return Response.status(400)
                        .entity(new ErrorResponse("BAD_REQUEST", "content is required", 400))
                        .build();
            }

            String principal = resolvePrincipal(securityContext);

            InternalComment comment = new InternalComment();
            comment.ensureCommentId();
            comment.authorId = principal;
            comment.content = content;
            comment.createdAt = Instant.now();

            if (request.internalComments == null) request.internalComments = new ArrayList<>();
            request.internalComments.add(comment);

            request.updatedAt = Instant.now();
            request.updatedBy = principal;
            request.update();
            return Response.ok(request).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /feature-requests/{id}/assign
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Admin-only: assign the feature request to a user.
     */
    @POST
    @Path("{id}/assign")
    @RolesAllowed({"ROLE_ADMIN"})
    public Response assign(@PathParam("id") String id, Map<String, String> body,
                           @Context SecurityContext securityContext) {
        try {
            FeatureRequest request = FeatureRequest.findById(id);
            if (request == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Feature request not found: " + id, 404))
                        .build();
            }

            String assignedTo = body == null ? null : body.get("assignedTo");
            if (assignedTo == null || assignedTo.isBlank()) {
                return Response.status(400)
                        .entity(new ErrorResponse("BAD_REQUEST", "assignedTo is required", 400))
                        .build();
            }

            String principal = resolvePrincipal(securityContext);
            request.assignedTo = assignedTo;

            if (request.statusHistory == null) request.statusHistory = new ArrayList<>();
            StatusChange change = new StatusChange();
            change.fromStatus = request.status != null ? request.status.getValue() : null;
            change.toStatus = request.status != null ? request.status.getValue() : null;
            change.changedBy = principal;
            change.changedAt = Instant.now();
            change.reason = "Assigned to " + assignedTo;
            request.statusHistory.add(change);

            request.updatedAt = Instant.now();
            request.updatedBy = principal;
            request.update();
            return Response.ok(request).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String resolvePrincipal(SecurityContext securityContext) {
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            return "anonymous";
        }
        String name = securityContext.getUserPrincipal().getName();
        return (name == null || name.isBlank()) ? "anonymous" : name;
    }
}
