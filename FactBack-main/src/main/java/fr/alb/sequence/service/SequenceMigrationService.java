package fr.alb.sequence.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import fr.alb.billing.model.Invoice;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class SequenceMigrationService {

    private static final Logger LOGGER = Logger.getLogger(SequenceMigrationService.class);

    @Inject
    InvoiceSequenceService sequenceService;

    public MigrationReport run() {
        List<String> steps = new ArrayList<>();
        int draftFixed = 0;
        int legacyFinals = 0;

        // Step 1: ensure default sequences exist
        sequenceService.ensureDefaultSequences();
        steps.add("Default sequences verified (INVOICE_DRAFT, INVOICE_FINAL)");

        // Step 2: fix DRAFT invoices missing draftNumber
        MongoCollection<Document> invoiceCol = Invoice.mongoCollection().withDocumentClass(Document.class);

        AtomicLong legacyCounter = new AtomicLong(1);
        var cursor = invoiceCol.find(Filters.and(
            Filters.eq("status", "DRAFT"),
            Filters.or(
                Filters.exists("draftNumber", false),
                Filters.eq("draftNumber", null),
                Filters.eq("draftNumber", "")
            )
        )).sort(new Document("createdDate", 1));

        for (Document inv : cursor) {
            long n = legacyCounter.getAndIncrement();
            String legacyNum = "DFT-LEGACY-" + String.format("%04d", n);
            invoiceCol.updateOne(
                Filters.eq("_id", inv.get("_id")),
                Updates.set("draftNumber", legacyNum)
            );
            draftFixed++;
        }
        steps.add("Draft invoices without draftNumber fixed: " + draftFixed);

        // Step 3: renumber finals with UUID finalNumber using the sequence
        var finalCursor = invoiceCol.find(Filters.and(
            Filters.eq("status", "FINAL"),
            Filters.regex("finalNumber", "^[0-9a-f]{8}-[0-9a-f]{4}")
        )).sort(new Document("createdDate", 1));

        for (Document inv : finalCursor) {
            String newFinalNumber = sequenceService.nextFinalNumber();
            invoiceCol.updateOne(
                Filters.eq("_id", inv.get("_id")),
                Updates.set("finalNumber", newFinalNumber)
            );
            legacyFinals++;
        }
        steps.add("Final invoices renumbered from UUID to sequential: " + legacyFinals);

        LOGGER.infof("Sequence migration complete. Drafts fixed: %d, Finals renumbered: %d",
            draftFixed, legacyFinals);

        return new MigrationReport(true, steps, draftFixed, legacyFinals);
    }

    public record MigrationReport(
        boolean success,
        List<String> steps,
        int draftNumbersAssigned,
        int legacyFinalsPreserved
    ) {}
}
