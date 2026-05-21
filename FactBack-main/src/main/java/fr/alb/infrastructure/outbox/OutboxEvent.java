package fr.alb.infrastructure.outbox;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;

/**
 * Outbox pattern: events written atomically with business transactions,
 * then dispatched by the OutboxScheduler.
 *
 * Today: logs + marks SENT (no external bus).
 * Future: OutboxScheduler sends to Azure EventHub / Kafka.
 * Only OutboxScheduler changes — this entity stays the same.
 */
@MongoEntity(collection = "OUTBOX_EVENT")
public class OutboxEvent extends PanacheMongoEntityBase {

    @BsonId
    public ObjectId id;

    public String aggregateType;    // "Invoice", "ChargeRecord"
    public String aggregateId;      // the ID of the aggregate (invoice ID, etc.)
    public String eventType;        // "InvoiceFinalized", "InvoiceDraftCreated", "ChargeCalculated"
    public String payload;          // JSON-serialized event data

    public String status;           // PENDING, SENT, FAILED
    public int retryCount = 0;
    public String lastError;        // Error message on failure

    public Instant createdAt;
    public Instant processedAt;     // null until processed

    // --- Static finders ---

    public static List<OutboxEvent> findPending(int limit) {
        return find("status = ?1", "PENDING")
            .page(0, limit)
            .list();
    }

    public static List<OutboxEvent> findFailed() {
        return list("status", "FAILED");
    }

    // --- Factory methods ---

    public static OutboxEvent of(String aggregateType, String aggregateId,
                                  String eventType, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.aggregateType = aggregateType;
        e.aggregateId   = aggregateId;
        e.eventType     = eventType;
        e.payload       = payload;
        e.status        = "PENDING";
        e.retryCount    = 0;
        e.createdAt     = Instant.now();
        return e;
    }
}
