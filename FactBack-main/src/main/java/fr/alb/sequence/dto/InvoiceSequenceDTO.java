package fr.alb.sequence.dto;

public record InvoiceSequenceDTO(
    String id,
    String sequenceId,
    String prefix,
    long nextValue,
    int maximumDigits,
    String invoiceTypeId,
    boolean isDefault,
    String previewExample
) {}
