package fr.alb.ai.featurerequest.model;

import java.time.Instant;
import java.util.UUID;

/**
 * An internal admin-only comment on a FeatureRequest.
 */
public class InternalComment {

    /** Unique identifier — auto-generated if null/blank. */
    public String commentId;
    public String authorId;
    public String content;
    public Instant createdAt;

    /** Ensures commentId is set; call before persisting. */
    public void ensureCommentId() {
        if (commentId == null || commentId.isBlank()) {
            commentId = UUID.randomUUID().toString();
        }
    }
}
