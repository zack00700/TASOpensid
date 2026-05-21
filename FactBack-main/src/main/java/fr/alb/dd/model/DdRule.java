package fr.alb.dd.model;

import fr.alb.model.EntityBase;
import fr.alb.billing.model.RateManagement;

import java.util.ArrayList;
import java.util.List;

import fr.alb.type.DdClockAnchor;
import fr.alb.type.DdType;
import fr.alb.type.Status;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Demurrage & Detention rule defining free-day allowances and rate tiers
 * for a given carrier / container type combination.
 */
@MongoEntity(collection = "DD_RULE")
public class DdRule extends EntityBase {

    private static final long serialVersionUID = 1L;

    /** Human-readable name for this rule. */
    public String ruleName;

    /** Whether this rule applies to demurrage or detention. */
    public DdType ddType;

    /** Event that starts the clock for this rule. */
    public DdClockAnchor clockAnchor;

    /** Carrier this rule is scoped to; null means it is the default rule. */
    public String carrierId;

    /** Container type code this rule applies to; null means all types. */
    public String containerTypeCode;

    /** Number of standard free days granted before charges begin. */
    public int freeDays;

    /** Rate tiers defining the per-day charge per band. */
    public List<RateManagement> tiers;

    /** If true, holidays count toward the free-day consumption. */
    public boolean includeHolidays;

    /** If true, weekends count toward the free-day consumption. */
    public boolean includeWeekends;

    /** ACTIVE / INACTIVE lifecycle status. */
    public Status status;

    /** Optional internal notes. */
    public String notes;

    public DdRule() {
        super();
        this.tiers = new ArrayList<>();
        this.status = Status.ACTIVE;
    }
}
