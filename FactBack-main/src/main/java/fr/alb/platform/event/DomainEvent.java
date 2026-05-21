package fr.alb.platform.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker interface for every cross-context domain event.
 *
 * <p>A domain event names something that happened in one context and is of
 * interest to other contexts or to the read model. Concrete events are
 * immutable value objects living in the emitting context's {@code event}
 * package (e.g. {@code fr.alb.billing.event.InvoiceFinalized}).
 *
 * <p>Publication goes through {@link DomainEventPublisher}, which both
 * persists the event in the {@code DOMAIN_OUTBOX} Mongo collection (for
 * durability / future broker fan-out) and fires it through CDI for same-JVM
 * listeners. Context code MUST NOT inject {@code jakarta.enterprise.event.Event}
 * directly — always publish through the {@link DomainEventPublisher} so the
 * outbox stays in sync.
 *
 * <p>Event types follow the pattern {@code "<context>.<EventName>"} so the
 * catalogue in {@code docs/BOUNDED_CONTEXTS.md §4} stays readable from a
 * distance. Example: {@code "billing.InvoiceFinalized"}.
 */
public interface DomainEvent {

    /** Stable dotted identifier — {@code "<context>.<EventName>"}. */
    String eventType();

    /** ID of the aggregate this event is about (invoice id, vessel visit id…). */
    String aggregateId();

    /** When the event happened. Defaults to the moment the event is instantiated. */
    Instant occurredAt();

    /** Idempotency key so a listener can deduplicate. Defaults to a random UUID. */
    default String eventId() {
        return UUID.randomUUID().toString();
    }
}
