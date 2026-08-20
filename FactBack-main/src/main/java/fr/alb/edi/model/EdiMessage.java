package fr.alb.edi.model;

import fr.alb.model.EntityBase;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Inbound or outbound EDI message record.
 *
 * Stores the raw EDI payload plus processing metadata for audit and replay.
 * Follows the N4 TAS EDI integration model (EDIFACT / X12 / CSV).
 */
@MongoEntity(collection = "EDI_MESSAGE")
public class EdiMessage extends EntityBase {

    public static final long serialVersionUID = 1L;

    public enum Direction { INBOUND, OUTBOUND }

    public enum EdiStatus { RECEIVED, PROCESSING, PROCESSED, FAILED, SKIPPED }

    public enum EdiFormat { EDIFACT, X12, CSV, JSON, XML }

    /** Direction of message relative to the terminal. */
    public Direction direction;

    /** Format of the raw payload. */
    public EdiFormat format;

    /** EDI message type / transaction set (e.g. "CUSCAR", "214", "COPRAR"). */
    public String messageType;

    /** Trading partner or sender ID. */
    public String partnerId;

    /** Raw EDI content (string; store as-is for replay capability). */
    public String rawPayload;

    /** Current processing status. */
    public EdiStatus status;

    /** Optional reference to a processed entity (e.g. billOfLadingId, invoiceId). */
    public String relatedEntityId;

    /** Human-readable processing result or error message. */
    public String processingNote;

    /** When the message was received/sent. */
    public Instant messageDate;

    /** When processing completed (null while RECEIVED/PROCESSING). */
    public Instant processedAt;

    /** Number of processing attempts (for retry tracking). */
    public int attempts;

    public EdiMessage() {
        super();
        this.status = EdiStatus.RECEIVED;
        this.messageDate = Instant.now();
        this.attempts = 0;
    }

    /** A PROCESSING message older than this is considered crashed and re-claimable. */
    public static final Duration STALE_PROCESSING = Duration.ofMinutes(10);

    /**
     * Atomically claim this message for processing (M14). Matches RECEIVED,
     * FAILED, SKIPPED, or a PROCESSING row whose updatedAt is stale (crashed
     * worker) — and flips it to PROCESSING while bumping attempts, in a single
     * findOneAndUpdate so two concurrent workers cannot both win.
     *
     * @return the freshly claimed message, or null when the message is
     *         missing, terminal (PROCESSED), or currently being processed.
     */
    public static EdiMessage claim(String id) {
        Date now = new Date();
        Date staleCutoff = Date.from(now.toInstant().minus(STALE_PROCESSING));
        MongoCollection<Document> coll = mongoCollection().withDocumentClass(Document.class);
        Bson eligible = Filters.or(
                Filters.in("status", "RECEIVED", "FAILED", "SKIPPED"),
                Filters.and(
                        Filters.eq("status", "PROCESSING"),
                        Filters.or(
                                Filters.lt("updatedAt", staleCutoff),
                                Filters.exists("updatedAt", false))));
        Bson update = Updates.combine(
                Updates.set("status", "PROCESSING"),
                Updates.inc("attempts", 1),
                Updates.set("updatedAt", now));
        Document doc = coll.findOneAndUpdate(
                Filters.and(Filters.eq("_id", id), eligible),
                update,
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (doc == null) {
            return null;
        }
        // We own the claim now — reloading as a typed entity is race-free.
        return findById(id);
    }
}
