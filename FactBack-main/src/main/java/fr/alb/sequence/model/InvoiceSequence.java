package fr.alb.sequence.model;

import io.quarkus.mongodb.panache.common.MongoEntity;
import fr.alb.model.EntityBase;

@MongoEntity(collection = "INVOICE_SEQUENCE")
public class InvoiceSequence extends EntityBase {

    public String sequenceId;     // unique identifier: "INVOICE_FINAL", "INVOICE_DRAFT"
    public String prefix;         // number prefix: "INV", "DFT", "MRN", "" (empty)
    public long nextValue;        // next value to assign
    public int maximumDigits;     // padded length (5 → 00001)
    public String invoiceTypeId;  // null = global sequence
    public boolean isDefault;     // true for INVOICE_DRAFT / INVOICE_FINAL
}
