package fr.alb.billing.model;

import java.math.BigDecimal;
import java.util.List;

import fr.alb.billing.model.RateManagement.TaxBreakdownItem;

public class InvoiceLineSnap {
    public String itemId;
    public String itemNumber;
    public String description;
    public BigDecimal quantity;
    public String uom;
    public BigDecimal unitPrice;
    public BigDecimal amount;
    public String currency;

    // (Optional) provenance
    public String lifecycleId;
    public String eventId;
    public String contractId;
    public String contractName;
    public String contractRateId;
    public BigDecimal taxTotal;
    public BigDecimal inclusiveTaxTotal;
    public BigDecimal exclusiveTaxTotal;
    public BigDecimal finalAmount;
    public List<TaxBreakdownItem> taxBreakdown;
    public String taxCalculationId;
}

