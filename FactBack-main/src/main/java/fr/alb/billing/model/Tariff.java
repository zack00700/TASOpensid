package fr.alb.billing.model;

import fr.alb.model.EntityBase;

import java.util.Date;
import java.util.List;

import fr.alb.type.ServiceType;
import fr.alb.type.Status;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Master tariff definition — the rate schedule for a given service type.
 *
 * A Tariff holds the pricing rules (rates + calculationMode). Contracts link
 * customers to a Tariff. This separates "what we charge" (Tariff) from
 * "who we charge it to" (Contract), following the N4 TAS model.
 */
@MongoEntity(collection = "TARIFF")
public class Tariff extends EntityBase {

    public static final long serialVersionUID = 1L;

    /** Display name (e.g. "Storage 2024", "THC Standard"). */
    public String name;

    public String description;

    /** The type of terminal service this tariff covers. */
    public ServiceType serviceType;

    /** Active/Inactive/Draft. */
    public Status status;

    /** Validity window for this tariff. */
    public Date startDate;
    public Date endDate;

    /**
     * How the billable quantity is derived and which rates apply.
     * Mirrors Contract.calculationMode — moved here so multiple Contracts
     * can share the same tariff logic.
     */
    public CalculationMode calculationMode;

    /**
     * Rate schedule — one or more tiers/bands depending on rateType.
     * Use RateManagement.rateType to distinguish SIMPLE / TIERED / BANDED / VOLUME.
     */
    public List<RateManagement> rates;

    /** Free text notes visible to billing admins. */
    public String notes;

    public Tariff() {
        super();
        this.status = Status.ACTIVE;
    }

    @Override
    public String toString() {
        return "Tariff[id=" + id + ", name=" + name + ", serviceType=" + serviceType
                + ", status=" + status + ", startDate=" + startDate + ", endDate=" + endDate + "]";
    }
}
