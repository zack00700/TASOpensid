package fr.alb.sequence.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import fr.alb.sequence.dto.InvoiceSequenceCreateDTO;
import fr.alb.sequence.model.InvoiceSequence;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.bson.Document;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class InvoiceSequenceService {

    private static final Logger LOGGER = Logger.getLogger(InvoiceSequenceService.class);
    public static final String DEFAULT_DRAFT_ID = "INVOICE_DRAFT";
    public static final String DEFAULT_FINAL_ID = "INVOICE_FINAL";

    @PostConstruct
    void ensureIndexes() {
        try {
            getCollection().createIndex(
                Indexes.ascending("sequenceId"),
                new IndexOptions().unique(true)
            );
        } catch (Exception e) {
            LOGGER.warn("Could not create index on INVOICE_SEQUENCE: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Obtain next number — ATOMIC OPERATION
    // -------------------------------------------------------------------------

    /**
     * Atomically fetches and reserves the next number from the given sequence.
     * Uses findOneAndUpdate with $inc:{nextValue:1} — atomicity guaranteed by MongoDB.
     *
     * Rollover: when nextValue exceeds 10^maximumDigits - 1, resets to 1.
     *
     * @param sequenceId sequence identifier
     * @return formatted number with prefix and padding, e.g. "INV00042"
     */
    public String nextFormattedNumber(String sequenceId) {
        MongoCollection<Document> col = getCollection();

        Document before = col.findOneAndUpdate(
            Filters.eq("sequenceId", sequenceId),
            Updates.inc("nextValue", 1L),
            new FindOneAndUpdateOptions()
                .returnDocument(ReturnDocument.BEFORE)
                .upsert(false)
        );

        if (before == null) {
            throw new NotFoundException("Secuencia no encontrada: " + sequenceId);
        }

        long assignedValue = before.getLong("nextValue");
        int maxDigits = before.getInteger("maximumDigits");
        String prefix = before.getString("prefix");
        if (prefix == null) prefix = "";

        long maxValue = (long) Math.pow(10, maxDigits) - 1;

        if (assignedValue > maxValue) {
            LOGGER.warnf("Sequence %s reached maximum (%d). Resetting to 1.", sequenceId, maxValue);
            col.updateOne(
                Filters.eq("sequenceId", sequenceId),
                Updates.set("nextValue", 2L)
            );
            return prefix + String.format("%0" + maxDigits + "d", 1L);
        }

        return prefix + String.format("%0" + maxDigits + "d", assignedValue);
    }

    public String nextDraftNumber() {
        return nextFormattedNumber(DEFAULT_DRAFT_ID);
    }

    public String nextFinalNumber() {
        return nextFormattedNumber(DEFAULT_FINAL_ID);
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    public List<InvoiceSequence> listAll() {
        return InvoiceSequence.listAll(Sort.ascending("sequenceId"));
    }

    public InvoiceSequence findBySequenceId(String sequenceId) {
        return InvoiceSequence.<InvoiceSequence>find("sequenceId", sequenceId)
            .firstResultOptional()
            .orElseThrow(() -> new NotFoundException("Secuencia no encontrada: " + sequenceId));
    }

    public String previewNextNumber(String sequenceId) {
        InvoiceSequence seq = findBySequenceId(sequenceId);
        String safePrefix = seq.prefix != null ? seq.prefix : "";
        long maxValue = (long) Math.pow(10, seq.maximumDigits) - 1;
        long displayValue = Math.min(seq.nextValue, maxValue);
        return safePrefix + String.format("%0" + seq.maximumDigits + "d", displayValue);
    }

    public InvoiceSequence create(InvoiceSequenceCreateDTO dto) {
        String normalizedId = dto.sequenceId().trim().toUpperCase();
        long count = InvoiceSequence.count("sequenceId", normalizedId);
        if (count > 0) {
            throw new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                    .entity("{\"message\":\"Ya existe una secuencia con ID: " + normalizedId + "\"}")
                    .build()
            );
        }
        InvoiceSequence seq = new InvoiceSequence();
        seq.sequenceId = normalizedId;
        seq.prefix = dto.prefix() != null ? dto.prefix() : "";
        seq.nextValue = dto.nextValue();
        seq.maximumDigits = dto.maximumDigits();
        seq.invoiceTypeId = dto.invoiceTypeId();
        seq.isDefault = false;
        seq.createdAt = Instant.now();
        seq.updatedAt = Instant.now();
        seq.persist();
        return seq;
    }

    public InvoiceSequence update(String sequenceId, InvoiceSequenceCreateDTO dto) {
        InvoiceSequence existing = findBySequenceId(sequenceId);
        existing.prefix = dto.prefix() != null ? dto.prefix() : "";
        existing.nextValue = dto.nextValue();
        existing.maximumDigits = dto.maximumDigits();
        existing.invoiceTypeId = dto.invoiceTypeId();
        existing.updatedAt = Instant.now();
        existing.update();
        return existing;
    }

    // -------------------------------------------------------------------------
    // Default sequence initialization
    // -------------------------------------------------------------------------

    public void ensureDefaultSequences() {
        try {
            upsertDefault(DEFAULT_DRAFT_ID, "DFT", 1L, 5);
            upsertDefault(DEFAULT_FINAL_ID, "INV", 1L, 5);
            LOGGER.info("Default invoice sequences verified.");
        } catch (Exception e) {
            LOGGER.warn("Could not ensure default sequences: " + e.getMessage());
        }
    }

    public void setNextValue(String sequenceId, long value) {
        InvoiceSequence seq = findBySequenceId(sequenceId);
        seq.nextValue = value;
        seq.updatedAt = Instant.now();
        seq.update();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void upsertDefault(String id, String prefix, long start, int digits) {
        long count = InvoiceSequence.count("sequenceId", id);
        if (count == 0) {
            InvoiceSequence s = new InvoiceSequence();
            s.sequenceId = id;
            s.prefix = prefix;
            s.nextValue = start;
            s.maximumDigits = digits;
            s.isDefault = true;
            s.createdAt = Instant.now();
            s.updatedAt = Instant.now();
            s.persist();
            LOGGER.infof("Created default sequence: %s", id);
        }
    }

    private MongoCollection<Document> getCollection() {
        return InvoiceSequence.mongoCollection().withDocumentClass(Document.class);
    }
}
