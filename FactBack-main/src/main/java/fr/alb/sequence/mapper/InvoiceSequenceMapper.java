package fr.alb.sequence.mapper;

import fr.alb.sequence.dto.InvoiceSequenceDTO;
import fr.alb.sequence.model.InvoiceSequence;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InvoiceSequenceMapper {

    public InvoiceSequenceDTO toDTO(InvoiceSequence s) {
        long maxVal = (long) Math.pow(10, s.maximumDigits) - 1;
        String safePrefix = s.prefix != null ? s.prefix : "";
        long displayValue = Math.min(s.nextValue, maxVal);
        String example = safePrefix + String.format("%0" + s.maximumDigits + "d", displayValue);
        return new InvoiceSequenceDTO(
            s.getId(),
            s.sequenceId,
            s.prefix,
            s.nextValue,
            s.maximumDigits,
            s.invoiceTypeId,
            s.isDefault,
            example
        );
    }
}
