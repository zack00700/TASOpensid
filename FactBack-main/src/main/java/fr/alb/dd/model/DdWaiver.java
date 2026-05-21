package fr.alb.dd.model;

import fr.alb.model.EntityBase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import fr.alb.type.WaiverType;

/**
 * Waiver record embedded in a DdAccrual.
 * Plain POJO — not a Mongo entity on its own.
 */
public class DdWaiver {

    /** Unique identifier for this waiver (UUID). */
    public String waiverId;

    /** Category of waiver applied. */
    public WaiverType waiverType;

    /** Total amount waived. */
    public BigDecimal waivedAmount;

    /** Number of additional free days granted (relevant for FREE_DAYS_EXTENSION). */
    public int extensionDays;

    /** Business reason justifying the waiver. */
    public String reason;

    /** Username of the approver. */
    public String approvedBy;

    /** Timestamp when the waiver was approved. */
    public Instant approvedAt;

    public DdWaiver() {}

    /**
     * Ensures waiverId is set; generates a UUID if null or blank.
     */
    public void ensureWaiverId() {
        if (waiverId == null || waiverId.isBlank()) {
            waiverId = UUID.randomUUID().toString();
        }
    }
}
