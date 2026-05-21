package fr.alb.edi.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/**
 * Fired after an inbound EDI message has been parsed and mapped into
 * domain objects.
 *
 * <p>This event is intentionally generic — the detailed business event
 * (e.g. {@code bol.BolRegistered} when a COPRAR is processed) is published
 * separately by the corresponding parser so listeners can subscribe to the
 * specific intent rather than to every EDI message type.
 */
public class EdiMessageIngested implements DomainEvent {

    public final String ediMessageId;
    public final String messageType;
    public final String partnerId;
    public final String relatedEntityId;
    public final Instant ingestedAt;

    public EdiMessageIngested(String ediMessageId,
                              String messageType,
                              String partnerId,
                              String relatedEntityId,
                              Instant ingestedAt) {
        this.ediMessageId = ediMessageId;
        this.messageType = messageType;
        this.partnerId = partnerId;
        this.relatedEntityId = relatedEntityId;
        this.ingestedAt = ingestedAt != null ? ingestedAt : Instant.now();
    }

    @Override public String eventType()   { return "edi.MessageIngested"; }
    @Override public String aggregateId() { return ediMessageId; }
    @Override public Instant occurredAt() { return ingestedAt; }
}
