package fr.alb.edi;

import fr.alb.bol.model.BillOfLading;
import fr.alb.edi.model.EdiMessage;
import fr.alb.yard.model.Item;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-level tests (M14): idempotent COPRAR dedup by blNumber, and the
 * EdiMessage.claim() state rules (terminal PROCESSED, fresh vs stale
 * PROCESSING).
 *
 * Requires a real MongoDB (runs in CI).
 */
@QuarkusTest
class EdiProcessorDedupAndClaimTest {

    private static final String BL_NUMBER = "BL-DEDUP-1";

    @Inject
    EdiProcessorService processorService;

    @BeforeEach
    void clean() {
        EdiMessage.deleteAll();
        Item.deleteAll();
        BillOfLading.delete("blNumber", BL_NUMBER);
    }

    private EdiMessage coprarMessage() {
        EdiMessage msg = new EdiMessage();
        msg.direction = EdiMessage.Direction.INBOUND;
        msg.format = EdiMessage.EdiFormat.EDIFACT;
        msg.messageType = "COPRAR";
        msg.rawPayload = "BOL|" + BL_NUMBER + "|MSC ANNA|V001|FRLEH|MACAS|S|C\n"
                + "CTR|MSCU0000001|40HC|10000|FULL||";
        msg.persist();
        return msg;
    }

    @Test
    void coprar_existingBol_isSkippedIdempotently() {
        BillOfLading existing = new BillOfLading();
        existing.setBlNumber(BL_NUMBER);
        existing.persist();

        EdiMessage msg = coprarMessage();
        processorService.process(msg.id);

        EdiMessage after = EdiMessage.findById(msg.id);
        assertEquals(EdiMessage.EdiStatus.PROCESSED, after.status);
        assertTrue(after.processingNote.contains("already exists"),
                "note must flag the duplicate, was: " + after.processingNote);
        assertEquals(existing.getId(), after.relatedEntityId,
                "relatedEntityId must point at the pre-existing BOL");
        assertEquals(1, BillOfLading.count("blNumber", BL_NUMBER), "no second BOL");
        assertEquals(0, Item.count(), "no item may be persisted on dedup skip");
    }

    @Test
    void claim_processedMessage_returnsNull() {
        EdiMessage msg = coprarMessage();
        processorService.process(msg.id);
        assertEquals(EdiMessage.EdiStatus.PROCESSED,
                ((EdiMessage) EdiMessage.findById(msg.id)).status);

        assertNull(EdiMessage.claim(msg.id), "PROCESSED is terminal — not claimable");
        EdiMessage after = EdiMessage.findById(msg.id);
        assertEquals(1, after.attempts, "failed claim must not bump attempts");
    }

    @Test
    void claim_freshProcessing_returnsNull_staleProcessing_isReclaimable() {
        EdiMessage msg = coprarMessage();

        assertNotNull(EdiMessage.claim(msg.id), "RECEIVED must be claimable");
        assertNull(EdiMessage.claim(msg.id), "fresh PROCESSING must not be re-claimable");

        // Simulate a worker that crashed 11 minutes ago.
        EdiMessage stale = EdiMessage.findById(msg.id);
        stale.updatedAt = Instant.now().minus(11, ChronoUnit.MINUTES);
        stale.update();

        EdiMessage reclaimed = EdiMessage.claim(msg.id);
        assertNotNull(reclaimed, "stale PROCESSING (>10 min) must be re-claimable");
        // Two successful claims only (RECEIVED→PROCESSING, stale→PROCESSING);
        // the failed claim in between must not increment.
        assertEquals(2, reclaimed.attempts, "each successful claim bumps attempts");
    }
}
