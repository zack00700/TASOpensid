package fr.alb.sequence.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record InvoiceSequenceCreateDTO(
    @NotBlank String sequenceId,
    String prefix,
    @Min(1) long nextValue,
    @Min(1) @Max(10) int maximumDigits,
    String invoiceTypeId
) {}
