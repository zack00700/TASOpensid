package fr.alb.customs.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/**
 * Fired when customs permanently rejects a declaration. Items stay
 * blocked; a new declaration must be created to resume the flow.
 */
public class DeclarationRejected implements DomainEvent {

    public final String declarationId;
    public final String billOfLadingId;
    public final String reason;
    public final Instant rejectedAt;

    public DeclarationRejected(String declarationId, String billOfLadingId,
                               String reason, Instant rejectedAt) {
        this.declarationId = declarationId;
        this.billOfLadingId = billOfLadingId;
        this.reason = reason;
        this.rejectedAt = rejectedAt != null ? rejectedAt : Instant.now();
    }

    @Override public String eventType()   { return "customs.DeclarationRejected"; }
    @Override public String aggregateId() { return declarationId; }
    @Override public Instant occurredAt() { return rejectedAt; }
}
