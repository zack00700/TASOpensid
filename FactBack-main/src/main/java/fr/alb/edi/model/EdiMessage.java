package fr.alb.edi.model;

import fr.alb.model.EntityBase;

import java.time.Instant;

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
}
