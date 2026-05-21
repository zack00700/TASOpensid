package fr.alb.dto;

import java.util.Date;
import java.util.List;

import fr.alb.billing.model.CalculationMode;
import fr.alb.billing.model.ContractAddendum;
import fr.alb.billing.model.RateManagement;
import fr.alb.type.Status;

/**
 * DTO used for contract update requests.
 * All fields are optional to support partial updates.
 */
public class ContractUpdateDTO {
    public String name;
    public String description;
    public CalculationMode calculationMode;
    public Status status;
    public Date startDate;
    public Date endDate;
    public List<RateManagement> rates;
    // N4 extensions
    public String tariffId;
    public String customerId;
    public String customerName;
    public Integer priority;
    public List<ContractAddendum> addendums;
}

