package fr.alb.platform.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alb.infrastructure.outbox.OutboxEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Single entry point every context uses to publish a {@link DomainEvent}.
 *
 * <p>One call does two things:
 * <ol>
 *     <li>Persists an {@link OutboxEvent} in {@code OUTBOX_EVENT} so the
 *         event survives crashes and can be replayed or shipped to an
 *         external broker by the scheduler in
 *         {@code fr.alb.infrastructure.scheduler.OutboxScheduler}.</li>
 *     <li>Fires the event through CDI so same-JVM listeners (Ask AI
 *         projections, in-process reactions) observe it immediately.</li>
 * </ol>
 *
 * <p>Contexts must depend on this class, never on
 * {@code jakarta.enterprise.event.Event} directly — otherwise the outbox row
 * is missed and downstream consumers end up desynchronised.
 *
 * <p>The legacy {@code OutboxEvent} + scheduler predate the bounded-context
 * refactor; they live in {@code fr.alb.infrastructure.outbox} /
 * {@code fr.alb.infrastructure.scheduler} and will migrate to
 * {@code fr.alb.platform.outbox} in a follow-up.
 */
@ApplicationScoped
public class DomainEventPublisher {

    private static final Logger LOG = Logger.getLogger(DomainEventPublisher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    Event<DomainEvent> cdiEvent;

    public void publish(DomainEvent event) {
        if (event == null) {
            LOG.warn("publish(null) called — ignoring.");
            return;
        }

        String payloadJson;
        try {
            payloadJson = MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to serialize DomainEvent of type %s — outbox row will have empty payload",
                    event.eventType());
            payloadJson = "{}";
        }

        String aggregateType = aggregateTypeFrom(event.eventType());
        OutboxEvent row = OutboxEvent.of(
                aggregateType,
                event.aggregateId(),
                event.eventType(),
                payloadJson);
        row.persist();

        LOG.debugf("Outbox row inserted for %s (aggregateId=%s)",
                event.eventType(), event.aggregateId());

        cdiEvent.fire(event);
    }

    /**
     * Event types follow the pattern {@code "<context>.<EventName>"}. The
     * legacy {@code aggregateType} column predates the naming convention, so
     * we derive it from the event name (e.g. "billing.InvoiceFinalized"
     * → "Invoice"). Fallback to the full event tail when the pattern does
     * not match.
     */
    private static String aggregateTypeFrom(String eventType) {
        if (eventType == null) return "Unknown";
        int dot = eventType.indexOf('.');
        if (dot < 0 || dot == eventType.length() - 1) return eventType;
        String tail = eventType.substring(dot + 1);
        for (String suffix : new String[] {
                "Finalized", "Registered", "Issued", "Moved", "Berthed",
                "Departed", "Arrived", "Delivered", "Created", "Ticked",
                "Ingested"}) {
            if (tail.endsWith(suffix)) {
                return tail.substring(0, tail.length() - suffix.length());
            }
        }
        return tail;
    }
}
