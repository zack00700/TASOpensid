package fr.alb.dd.model;

import fr.alb.model.EntityBase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import fr.alb.type.DdAccrualStatus;
import fr.alb.type.DdClockAnchor;
import fr.alb.type.DdType;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Running D&D accrual for a single container per type (demurrage or detention).
 * Contains an append-only daily log and waiver history.
 */
@MongoEntity(collection = "DD_ACCRUAL")
public class DdAccrual extends EntityBase {

    private static final long serialVersionUID = 1L;

    /** Foreign key to the Item this accrual belongs to. */
    public String itemId;

    /** Container number for quick reference without joining Item. */
    public String containerNumber;

    /** Whether this is a demurrage or detention accrual. */
    public DdType ddType;

    /** Clock anchor event that triggered the start of this accrual. */
    public DdClockAnchor clockAnchor;

    /** Foreign key to the DdRule used to calculate this accrual. */
    public String ruleId;

    /** Carrier associated with this accrual. */
    public String carrierId;

    /** Total free days granted (from rule + any extensions). */
    public int freeDaysGranted;

    /** Timestamp when the D&D clock started ticking. */
    public Instant clockStart;

    /** Timestamp when the clock stopped (gate-out for demurrage, return for detention). */
    public Instant clockStop;

    /** Total calendar days elapsed between clockStart and clockStop (or now if RUNNING). */
    public int totalDaysElapsed;

    /** Days subject to charges: totalDaysElapsed - freeDaysGranted - holidayDays. */
    public int chargeableDays;

    /** Number of holiday days excluded from chargeable calculation. */
    public int holidayDays;

    /** Cumulative charge amount accrued so far. */
    public BigDecimal totalAccruedAmount;

    /** Current lifecycle status of this accrual. Defaults to RUNNING. */
    public DdAccrualStatus status;

    /** Append-only daily audit trail — one entry per calendar day. */
    public List<DdDayEntry> dailyLog;

    /** Waivers applied to this accrual. */
    public List<DdWaiver> waivers;

    /** Invoice ID once this accrual has been invoiced. */
    public String invoiceId;

    /** Invoice line ID within the invoice. */
    public String invoiceLineId;

    /** Optional internal notes. */
    public String notes;

    public DdAccrual() {
        super();
        this.status = DdAccrualStatus.RUNNING;
        this.dailyLog = new ArrayList<>();
        this.waivers = new ArrayList<>();
        this.totalAccruedAmount = BigDecimal.ZERO;
    }
}
