package fr.alb.engine.contract;

import fr.alb.billing.model.Contract;
import fr.alb.billing.model.RateManagement;

/**
 * Result of contract resolution. Contains the winning contract,
 * its selected rate, and the reason it was chosen (for audit).
 */
public record ContractMatch(
    Contract contract,
    RateManagement rate,
    String reason   // e.g. "Date window match, priority=2, UoM=DAY"
) {}
