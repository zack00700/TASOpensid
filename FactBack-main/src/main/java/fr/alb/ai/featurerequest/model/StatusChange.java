package fr.alb.ai.featurerequest.model;

import java.time.Instant;

/**
 * Immutable audit trail entry for a status transition on a FeatureRequest.
 */
public class StatusChange {

    public String fromStatus;
    public String toStatus;
    public String changedBy;
    public Instant changedAt;
    /** Optional note explaining the reason for the transition. */
    public String reason;
}
