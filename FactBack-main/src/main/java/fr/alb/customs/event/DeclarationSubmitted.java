package fr.alb.customs.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;
import java.util.List;

/**
 * Fired when a {@code CustomsDeclaration} transitions from DRAFT to
 * SUBMITTED — i.e. it has been handed to the customs authority.
 *
 * <p>Listeners: EDI outbound CUSCAR generation (future), notification
 * to the declarant's broker, projections that track clearance SLAs.
 */
public class DeclarationSubmitted implements DomainEvent {

    public final String declarationId;
    public final String billOfLadingId;
    public final List<String> itemIds;
    public final String declarationReference;
    public final Instant submittedAt;

    public DeclarationSubmitted(String declarationId, String billOfLadingId,
                                List<String> itemIds, String declarationReference,
                                Instant submittedAt) {
        this.declarationId = declarationId;
        this.billOfLadingId = billOfLadingId;
        this.itemIds = itemIds != null ? List.copyOf(itemIds) : List.of();
        this.declarationReference = declarationReference;
        this.submittedAt = submittedAt != null ? submittedAt : Instant.now();
    }

    @Override public String eventType()   { return "customs.DeclarationSubmitted"; }
    @Override public String aggregateId() { return declarationId; }
    @Override public Instant occurredAt() { return submittedAt; }
}
