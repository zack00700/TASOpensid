package fr.alb.ai.featurerequest.model;

import fr.alb.model.EntityBase;
import fr.alb.type.FeatureRequestStatus;
import fr.alb.type.TicketCategory;
import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A feature or improvement request submitted by a user.
 * Goes through an AI-driven clarification phase before entering the backlog.
 */
@MongoEntity(collection = "FEATURE_REQUEST")
public class FeatureRequest extends EntityBase {

    public static final long serialVersionUID = 1L;

    /** Short title of the request. */
    public String title;

    /** Initial description provided by the user. */
    public String description;

    /** Current lifecycle status; defaults to DRAFT. */
    public FeatureRequestStatus status = FeatureRequestStatus.DRAFT;

    /** Conversation turns between user and AI clarification assistant. */
    public List<ConversationMessage> conversation = new ArrayList<>();

    /** True once the AI signals CLARIFICATION_COMPLETE. */
    public boolean clarificationsDone = false;

    /** Priority score 0-100; higher = more urgent. Default 50. */
    public int priority = 50;

    /** Estimated effort: "S", "M", "L", "XL". */
    public String estimatedEffort;

    /** Free-form tags (e.g. "reefer", "customs", "billing"). */
    public List<String> tags = new ArrayList<>();

    /** Username of the admin who approved this request. */
    public String approvedBy;

    /** When the request was approved. */
    public Instant approvedAt;

    /** Reason supplied when the request is rejected. */
    public String rejectedReason;

    /** Structured summary produced by the AI when clarification is complete (prose for humans). */
    public String structuredSummary;

    /**
     * Parsed JSON block emitted by the AI after CLARIFICATION_COMPLETE — rendered as a
     * structured card in the UI. Shape matches the schema in {@code FeatureRequestAiService}.
     */
    public Map<String, Object> structuredSummaryData;

    // ── Ticketing fields ──────────────────────────────────────────────────────

    /** Human-readable ticket number, e.g. "FR-2026-0001". Set on create, never changed. */
    public String ticketNumber;

    /** Predefined category for routing/reporting. */
    public TicketCategory category;

    /** Username of the person currently assigned to this request. */
    public String assignedTo;

    /** SLA deadline. */
    public Instant dueDate;

    /** Release milestone, e.g. "v1.2" or "Q2-2026". */
    public String milestone;

    /** Ordered audit trail of every status transition. */
    public List<StatusChange> statusHistory = new ArrayList<>();

    /** Internal admin-only comments (not visible to the submitter). */
    public List<InternalComment> internalComments = new ArrayList<>();

    public FeatureRequest() {
        super();
    }
}
