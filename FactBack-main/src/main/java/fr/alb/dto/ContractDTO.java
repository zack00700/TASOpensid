package fr.alb.dto;

import java.util.Date;
import java.util.List;

import fr.alb.billing.model.ContractAddendum;
import fr.alb.billing.model.RateManagement;
import fr.alb.type.Status;

/**
 * Data transfer object used for transferring contract information.
 */
public class ContractDTO {
    public String id;
    public String name;
    public String description;
    public CalculationModeDTO calculationMode;
    public Status status;
    public Date startDate;
    public Date endDate;
    public List<RateManagement> rates;
    // N4 extensions
    public String tariffId;
    public String customerId;
    public String customerName;
    public int priority;
    public List<ContractAddendum> addendums;
}

