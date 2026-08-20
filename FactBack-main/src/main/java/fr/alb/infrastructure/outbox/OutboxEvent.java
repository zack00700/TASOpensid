package fr.alb.infrastructure.outbox;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Date;
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

    public String status;           // PENDING, PROCESSING, SENT, FAILED
    public int retryCount = 0;
    public String lastError;        // Error message on failure

    public Instant createdAt;
    public Instant processedAt;     // null until processed
    public Instant claimedAt;      // when a dispatcher claimed it (PROCESSING); null otherwise

    // --- Static finders ---

    public static List<OutboxEvent> findPending(int limit) {
        return find("status = ?1", "PENDING")
            .page(0, limit)
            .list();
    }

    public static List<OutboxEvent> findFailed() {
        return list("status", "FAILED");
    }

    /**
     * Atomically claim the oldest PENDING event (M15): flip it to PROCESSING
     * and stamp claimedAt in a single findOneAndUpdate, so concurrent
     * dispatchers can never pick the same row. Returns null when nothing is
     * pending.
     */
    public static OutboxEvent claim() {
        Date now = new Date();
        MongoCollection<Document> coll = mongoCollection().withDocumentClass(Document.class);
        Bson update = Updates.combine(
                Updates.set("status", "PROCESSING"),
                Updates.set("claimedAt", now));
        Document doc = coll.findOneAndUpdate(
                Filters.eq("status", "PENDING"),
                update,
                new FindOneAndUpdateOptions()
                        .sort(Sorts.ascending("createdAt"))
                        .returnDocument(ReturnDocument.AFTER));
        if (doc == null) {
            return null;
        }
        return fromDocument(doc);
    }

    /**
     * Put PROCESSING rows claimed before {@code before} back to PENDING —
     * a dispatcher that crashed mid-flight left them stuck. Returns the
     * number of recovered rows.
     */
    public static long resetStuckProcessing(java.time.Instant before) {
        MongoCollection<Document> coll = mongoCollection().withDocumentClass(Document.class);
        return coll.updateMany(
                Filters.and(
                        Filters.eq("status", "PROCESSING"),
                        Filters.lt("claimedAt", Date.from(before))),
                Updates.combine(
                        Updates.set("status", "PENDING"),
                        Updates.unset("claimedAt")))
                .getModifiedCount();
    }

    private static OutboxEvent fromDocument(Document d) {
        OutboxEvent e = new OutboxEvent();
        e.id            = d.getObjectId("_id");
        e.aggregateType = d.getString("aggregateType");
        e.aggregateId   = d.getString("aggregateId");
        e.eventType     = d.getString("eventType");
        e.payload       = d.getString("payload");
        e.status        = d.getString("status");
        Integer rc      = d.getInteger("retryCount");
        e.retryCount    = rc != null ? rc : 0;
        e.lastError     = d.getString("lastError");
        e.createdAt     = d.getDate("createdAt") != null ? d.getDate("createdAt").toInstant() : null;
        e.processedAt   = d.getDate("processedAt") != null ? d.getDate("processedAt").toInstant() : null;
        e.claimedAt     = d.getDate("claimedAt") != null ? d.getDate("claimedAt").toInstant() : null;
        return e;
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
