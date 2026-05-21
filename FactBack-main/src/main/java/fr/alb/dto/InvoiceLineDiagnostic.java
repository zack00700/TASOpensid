package fr.alb.dto;

import java.time.LocalDate;

public record InvoiceLineDiagnostic(
    String itemId,
    String itemNumber,
    String stage,
    String reason,
    LocalDate inDate,
    Long days,
    String contractId,
    String uomTried,
    String currencyTried
) {}
