package fr.alb.billing.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A versioned amendment to a Contract.
 * Addendums can override rates for a specific validity window without
 * replacing the base contract.
 */
public class ContractAddendum {

    private String addendumId;
    private String description;
    private Instant validFrom;
    private Instant validTo;
    private List<RateManagement> rateOverrides;
    private String createdBy;
    private Instant createdAt;

    public ContractAddendum() {
        this.addendumId = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public String getAddendumId() { return addendumId; }
    public void setAddendumId(String addendumId) { this.addendumId = addendumId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }

    public Instant getValidTo() { return validTo; }
    public void setValidTo(Instant validTo) { this.validTo = validTo; }

    public List<RateManagement> getRateOverrides() { return rateOverrides; }
    public void setRateOverrides(List<RateManagement> rateOverrides) { this.rateOverrides = rateOverrides; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** True if this addendum is active at the given instant. */
    public boolean isActiveAt(Instant when) {
        if (when == null) return false;
        boolean afterStart = validFrom == null || !when.isBefore(validFrom);
        boolean beforeEnd = validTo == null || when.isBefore(validTo);
        return afterStart && beforeEnd;
    }
}
