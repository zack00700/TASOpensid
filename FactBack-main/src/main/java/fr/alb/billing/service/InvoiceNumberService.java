package fr.alb.billing.service;

import fr.alb.sequence.service.InvoiceSequenceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service for generating invoice numbers and identifiers.
 * Delegates to InvoiceSequenceService for sequential, formatted number generation.
 */
@ApplicationScoped
public class InvoiceNumberService {

    @Inject
    InvoiceSequenceService sequenceService;

    /**
     * Generates the next final number using the default INVOICE_FINAL sequence.
     * Result format: prefix + zero-padded number, e.g. "INV00042"
     */
    public String generateFinalNumber() {
        return sequenceService.nextFinalNumber();
    }

    /**
     * Generates the next draft number using the default INVOICE_DRAFT sequence.
     * Result format: prefix + zero-padded number, e.g. "DFT00042"
     */
    public String generateDraftNumber() {
        return sequenceService.nextDraftNumber();
    }

    /**
     * Generates the next final number using a custom sequence (for invoice types).
     * Falls back to the default INVOICE_FINAL sequence if customSequenceId is null.
     */
    public String generateFinalNumber(String customSequenceId) {
        if (customSequenceId != null && !customSequenceId.isBlank()) {
            return sequenceService.nextFormattedNumber(customSequenceId);
        }
        return sequenceService.nextFinalNumber();
    }
}
