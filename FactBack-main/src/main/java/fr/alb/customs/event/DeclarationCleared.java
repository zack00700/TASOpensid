package fr.alb.customs.event;

import fr.alb.platform.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Fired when customs clears a declaration — gate-out is now allowed for
 * every covered item.
 */
public class DeclarationCleared implements DomainEvent {

    public final String declarationId;
    public final String billOfLadingId;
    public final List<String> itemIds;
    public final BigDecimal assessedDuties;
    public final Instant clearedAt;

    public DeclarationCleared(String declarationId, String billOfLadingId,
                              List<String> itemIds, BigDecimal assessedDuties,
                              Instant clearedAt) {
        this.declarationId = declarationId;
        this.billOfLadingId = billOfLadingId;
        this.itemIds = itemIds != null ? List.copyOf(itemIds) : List.of();
        this.assessedDuties = assessedDuties;
        this.clearedAt = clearedAt != null ? clearedAt : Instant.now();
    }

    @Override public String eventType()   { return "customs.DeclarationCleared"; }
    @Override public String aggregateId() { return declarationId; }
    @Override public Instant occurredAt() { return clearedAt; }
}
