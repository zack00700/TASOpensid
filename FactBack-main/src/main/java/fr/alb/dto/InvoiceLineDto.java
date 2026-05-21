package fr.alb.dto;

import java.math.BigDecimal;
import java.util.List;

import fr.alb.billing.model.RateManagement;

public record InvoiceLineDto(
    String itemId,
    String itemNumber,
    String description,
    BigDecimal quantity,
    String uom,
    BigDecimal unitPrice,
    BigDecimal amount,
    String currency,
    String contractId,
    String contractRateId,
    List<RateManagement.RateTax> taxes
) {}

