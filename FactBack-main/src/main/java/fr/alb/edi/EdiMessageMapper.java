package fr.alb.edi;

import fr.alb.bol.model.BillOfLading;
import fr.alb.billing.model.Invoice;
import fr.alb.billing.model.InvoiceLineSnap;
import fr.alb.yard.model.Item;
import fr.alb.type.EmptyStatus;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

/**
 * Core EDI mapping engine.
 *
 * Converts raw pipe-delimited EDI-like payloads into domain objects and vice
 * versa.  No external EDI library is used — all parsing is plain Java string
 * splitting.
 */
@ApplicationScoped
public class EdiMessageMapper {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parse a COPRAR-like payload (container pre-advice) into a
     * {@link BillOfLading} and a list of {@link Item} objects.
     *
     * <p>Expected format (one record per line, pipe-delimited):
     * <pre>
     * BOL|{blNumber}|{vessel}|{voyage}|{portOfLoading}|{portOfDischarge}|{shipper}|{consignee}
     * CTR|{containerNumber}|{containerType}|{weightKg}|{emptyStatus}|{sealNumber}|{hazmatClass}
     * </pre>
     *
     * @param payload raw EDI text
     * @return a {@link ParseResult} containing the mapped objects and any warnings
     */
    public ParseResult parseCoprar(String payload) {
        ParseResult result = new ParseResult();

        if (payload == null || payload.isBlank()) {
            result.warnings.add("Empty payload — nothing to parse");
            return result;
        }

        String[] lines = payload.split("\n");

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("BOL|")) {
                result.billOfLading = parseBolLine(line, result.warnings);

            } else if (line.startsWith("CTR|")) {
                if (result.billOfLading == null) {
                    result.warnings.add("CTR line encountered before BOL line — skipping: " + line);
                    continue;
                }
                Item item = parseCtrLine(line, result.billOfLading.getId(), result.warnings);
                if (item != null) {
                    result.items.add(item);
                }

            } else {
                result.warnings.add("Unrecognised line prefix — skipping: " + line);
            }
        }

        return result;
    }

    /**
     * Parse a CUSCAR-like payload (customs cargo declaration) and return a list
     * of update instructions, one per container.
     *
     * <p>Expected line format:
     * <pre>
     * CTR|{containerNumber}|{customsStatus}|{hsCode}|{countryOfOrigin}
     * </pre>
     *
     * @param payload raw EDI text
     * @return list of {@link CuscarUpdate} objects
     */
    public List<CuscarUpdate> parseCuscar(String payload) {
        List<CuscarUpdate> updates = new ArrayList<>();

        if (payload == null || payload.isBlank()) {
            return updates;
        }

        String[] lines = payload.split("\n");

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || !line.startsWith("CTR|")) {
                continue;
            }

            String[] parts = line.split("\\|", -1);
            CuscarUpdate update = new CuscarUpdate();
            update.containerNumber  = safeGet(parts, 1);
            update.customsStatus    = safeGet(parts, 2);
            update.hsCode           = safeGet(parts, 3);
            update.countryOfOrigin  = safeGet(parts, 4);

            if (update.containerNumber != null) {
                updates.add(update);
            }
        }

        return updates;
    }

    /**
     * Generate a simplified outbound INVOIC payload from an {@link Invoice}.
     *
     * <p>Format:
     * <pre>
     * HDR|{invoiceNumber}|{customerName}|{invoiceDate}|{grandTotal}|{currency}
     * LIN|{description}|{quantity}|{uom}|{unitPrice}|{amount}|{currency}
     * ...
     * </pre>
     *
     * @param invoice the invoice to serialise
     * @return INVOIC payload string
     */
    public String generateInvoic(Invoice invoice) {
        if (invoice == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // HDR line
        String invoiceNumber  = invoice.finalNumber != null ? invoice.finalNumber : invoice.draftNumber;
        String customerName   = nvl(invoice.customerName);
        String invoiceDate    = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : "";
        String grandTotal     = invoice.grandTotalAmount != null ? invoice.grandTotalAmount.toPlainString() : "0";
        String currency       = nvl(invoice.currency);

        sb.append("HDR")
          .append('|').append(nvl(invoiceNumber))
          .append('|').append(customerName)
          .append('|').append(invoiceDate)
          .append('|').append(grandTotal)
          .append('|').append(currency);

        // LIN lines — one per invoice line
        if (invoice.lines != null) {
            for (InvoiceLineSnap line : invoice.lines) {
                sb.append('\n');
                sb.append("LIN")
                  .append('|').append(nvl(line.description))
                  .append('|').append(line.quantity != null ? line.quantity.toPlainString() : "")
                  .append('|').append(nvl(line.uom))
                  .append('|').append(line.unitPrice != null ? line.unitPrice.toPlainString() : "")
                  .append('|').append(line.amount != null ? line.amount.toPlainString() : "")
                  .append('|').append(nvl(line.currency));
            }
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Inner result types
    // -------------------------------------------------------------------------

    /** Container for the output of {@link #parseCoprar(String)}. */
    public static class ParseResult {
        public BillOfLading billOfLading;
        public List<Item> items    = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
    }

    /** Update instruction produced by {@link #parseCuscar(String)}. */
    public static class CuscarUpdate {
        public String containerNumber;
        public String customsStatus;
        public String hsCode;
        public String countryOfOrigin;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Map a BOL| line to a new BillOfLading. Returns null and adds a warning on failure. */
    private BillOfLading parseBolLine(String line, List<String> warnings) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 2) {
            warnings.add("Malformed BOL line (too few fields): " + line);
            return null;
        }
        BillOfLading bol = new BillOfLading();
        bol.setBlNumber(EdiFieldExtractor.clean(safeGet(parts, 1)));
        bol.setVessel(EdiFieldExtractor.clean(safeGet(parts, 2)));
        bol.setVoyage(EdiFieldExtractor.clean(safeGet(parts, 3)));
        bol.setPortOfLoading(EdiFieldExtractor.clean(safeGet(parts, 4)));
        bol.setPortOfDischarge(EdiFieldExtractor.clean(safeGet(parts, 5)));
        bol.setShipper(EdiFieldExtractor.clean(safeGet(parts, 6)));
        bol.setConsignee(EdiFieldExtractor.clean(safeGet(parts, 7)));
        return bol;
    }

    /** Map a CTR| line to a new Item. Returns null and adds a warning on failure. */
    private Item parseCtrLine(String line, String billOfLadingId, List<String> warnings) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 2) {
            warnings.add("Malformed CTR line (too few fields): " + line);
            return null;
        }

        Item item = new Item();
        item.setBillOfLadingId(billOfLadingId);
        item.setContainerNumber(EdiFieldExtractor.clean(safeGet(parts, 1)));
        item.setContainerType(EdiFieldExtractor.clean(safeGet(parts, 2)));

        // weight
        Double weight = EdiFieldExtractor.parseDouble(safeGet(parts, 3));
        item.setWeight(weight);

        // emptyStatus — tolerate unknown values
        String emptyRaw = EdiFieldExtractor.clean(safeGet(parts, 4));
        if (emptyRaw != null) {
            EmptyStatus es = parseEmptyStatus(emptyRaw, warnings);
            item.setEmptyStatus(es);
        }

        // sealNumber → stored as list
        String seal = EdiFieldExtractor.clean(safeGet(parts, 5));
        if (seal != null) {
            item.setSealNumbers(List.of(seal));
        }

        // hazmat
        String hazmatClass = EdiFieldExtractor.clean(safeGet(parts, 6));
        item.setHazmatClass(hazmatClass);
        item.setHazmatFlag(hazmatClass != null && !hazmatClass.isEmpty());

        return item;
    }

    /** Safe array access — returns null instead of throwing. */
    private static String safeGet(String[] arr, int index) {
        if (arr == null || index < 0 || index >= arr.length) {
            return null;
        }
        String v = arr[index].trim();
        return v.isEmpty() ? null : v;
    }

    /** Null-to-empty-string helper for EDI generation. */
    private static String nvl(String s) {
        return s != null ? s : "";
    }

    /**
     * Parse EmptyStatus from a raw string, tolerating unknown values.
     * Falls back to UNKNOWN and records a warning.
     */
    private static EmptyStatus parseEmptyStatus(String raw, List<String> warnings) {
        try {
            return EmptyStatus.fromValue(raw);
        } catch (IllegalArgumentException e) {
            // Try by enum name (FULL, EMPTY, UNKNOWN)
            try {
                return EmptyStatus.valueOf(raw.toUpperCase());
            } catch (IllegalArgumentException ex) {
                warnings.add("Unknown EmptyStatus value '" + raw + "' — defaulting to UNKNOWN");
                return EmptyStatus.UNKNOWN;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Item setters missing from Item class — accessed via existing setters only
    // The following methods are invoked via Item's public setters discovered
    // during the audit of Item.java.  They are referenced here to ensure
    // compile-time correctness notes are captured.
    //
    // Item.setContainerNumber  ✓ (verified below)
    // Item.setContainerType    ✓
    // Item.setWeight           ✓
    // Item.setEmptyStatus      ✓
    // Item.setSealNumbers      ✓
    // Item.setHazmatClass      ✓
    // Item.setHazmatFlag       ✓
    // Item.setBillOfLadingId   ✓
    // -------------------------------------------------------------------------
}
