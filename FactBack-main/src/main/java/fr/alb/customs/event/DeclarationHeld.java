package fr.alb.customs.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/**
 * Fired when customs authority puts a declaration on hold pending
 * inspection or additional documents. The covered items stay
 * gate-blocked.
 */
public class DeclarationHeld implements DomainEvent {

    public final String declarationId;
    public final String billOfLadingId;
    public final String reason;
    public final Instant heldAt;

    public DeclarationHeld(String declarationId, String billOfLadingId,
                           String reason, Instant heldAt) {
        this.declarationId = declarationId;
        this.billOfLadingId = billOfLadingId;
        this.reason = reason;
        this.heldAt = heldAt != null ? heldAt : Instant.now();
    }

    @Override public String eventType()   { return "customs.DeclarationHeld"; }
    @Override public String aggregateId() { return declarationId; }
    @Override public Instant occurredAt() { return heldAt; }
}
