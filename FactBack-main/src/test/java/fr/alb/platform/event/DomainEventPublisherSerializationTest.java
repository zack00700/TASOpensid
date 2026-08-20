package fr.alb.platform.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alb.infrastructure.outbox.OutboxEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The outbox payload must survive events carrying java.time fields (the
 * legacy 'new ObjectMapper()' had no jsr310 module and fell back to "{}").
 *
 * Requires a real MongoDB (runs in CI).
 */
@QuarkusTest
class DomainEventPublisherSerializationTest {

    /** Test-only event with an Instant field — no CDI listener reacts to it. */
    static class SerializationProbe implements DomainEvent {
        public final String probeId;
        public final Instant happenedAt;

        SerializationProbe(String probeId, Instant happenedAt) {
            this.probeId = probeId;
            this.happenedAt = happenedAt;
        }

        @Override public String eventType()   { return "test.SerializationProbe"; }
        @Override public String aggregateId() { return probeId; }
        @Override public Instant occurredAt() { return happenedAt; }
    }

    @Inject
    DomainEventPublisher publisher;

    @Inject
    ObjectMapper mapper;

    @BeforeEach
    void clean() {
        OutboxEvent.deleteAll();
    }

    @Test
    void publish_eventWithInstant_serializesFullPayload() throws Exception {
        Instant when = Instant.parse("2026-07-03T10:15:30Z");
        publisher.publish(new SerializationProbe("PROBE-1", when));

        OutboxEvent row = OutboxEvent.find("aggregateId", "PROBE-1").firstResult();
        assertNotNull(row, "publish must insert an outbox row");
        assertNotEquals("{}", row.payload, "payload must not fall back to the empty object");

        JsonNode node = mapper.readTree(row.payload);
        assertEquals("PROBE-1", node.get("probeId").asText());
        assertNotNull(node.get("happenedAt"), "the Instant field must be serialized");
    }
}
