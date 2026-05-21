package fr.alb.edi;

import fr.alb.edi.event.EdiMessageIngested;
import fr.alb.edi.model.EdiMessage;
import fr.alb.platform.event.DomainEventPublisher;
import fr.alb.yard.model.Item;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

/**
 * Processes a stored {@link EdiMessage} (already persisted in the DB) and maps
 * its raw payload to domain objects.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Status set to {@code PROCESSING} and attempt counter incremented.</li>
 *   <li>The appropriate parser is invoked based on {@code messageType}.</li>
 *   <li>Resulting domain objects are persisted.</li>
 *   <li>Status transitions to {@code PROCESSED} or {@code FAILED}.</li>
 * </ol>
 */
@ApplicationScoped
public class EdiProcessorService {

    private static final Logger LOG = Logger.getLogger(EdiProcessorService.class);

    @Inject
    EdiMessageMapper mapper;

    @Inject
    DomainEventPublisher domainEvents;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Process a single {@link EdiMessage} identified by its ID.
     *
     * @param ediMessageId the MongoDB string ID of the message
     */
    @Transactional
    public void process(String ediMessageId) {
        EdiMessage msg = EdiMessage.findById(ediMessageId);
        if (msg == null) {
            LOG.warnf("EdiProcessor: message %s not found", ediMessageId);
            return;
        }
        if (msg.status == EdiMessage.EdiStatus.PROCESSED) {
            LOG.infof("EdiProcessor: message %s already PROCESSED — skipping", ediMessageId);
            return;
        }

        msg.status = EdiMessage.EdiStatus.PROCESSING;
        msg.attempts++;
        msg.update();

        try {
            switch (msg.messageType != null ? msg.messageType.toUpperCase() : "") {
                case "COPRAR" -> processCoprar(msg);
                case "CUSCAR" -> processCuscar(msg);
                case "CODECO" -> processCodeco(msg);
                case "COPARN" -> processCoparn(msg);
                case "BAPLIE" -> processBaplie(msg);
                case "MOVINS" -> processMovins(msg);
                case "CALINF" -> processCalinf(msg);
                default -> {
                    msg.processingNote = "Unsupported messageType: " + msg.messageType;
                    msg.status = EdiMessage.EdiStatus.SKIPPED;
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "EdiProcessor: failed to process message %s", ediMessageId);
            msg.status = EdiMessage.EdiStatus.FAILED;
            msg.processingNote = e.getMessage();
        }

        msg.processedAt = Instant.now();
        msg.update();

        if (msg.status == EdiMessage.EdiStatus.PROCESSED) {
            domainEvents.publish(new EdiMessageIngested(
                    String.valueOf(msg.id),
                    msg.messageType,
                    msg.partnerId,
                    msg.relatedEntityId,
                    msg.processedAt));
        }
    }

    // -------------------------------------------------------------------------
    // Private handlers
    // -------------------------------------------------------------------------

    /**
     * Handle a COPRAR message: parse containers, persist BOL and Items.
     */
    private void processCoprar(EdiMessage msg) {
        EdiMessageMapper.ParseResult result = mapper.parseCoprar(msg.rawPayload);

        // Log any mapping warnings
        for (String warning : result.warnings) {
            LOG.warnf("EdiProcessor [COPRAR] %s: %s", msg.id, warning);
        }

        if (result.billOfLading == null) {
            msg.status = EdiMessage.EdiStatus.FAILED;
            msg.processingNote = "No BOL line found in COPRAR payload";
            return;
        }

        result.billOfLading.persist();

        List<fr.alb.yard.model.Item> items = result.items;
        for (fr.alb.yard.model.Item item : items) {
            item.persist();
        }

        String blNumber = result.billOfLading.getBlNumber();
        msg.relatedEntityId = result.billOfLading.getId();
        msg.processingNote = "Created BOL " + blNumber + " with " + items.size() + " containers";
        msg.status = EdiMessage.EdiStatus.PROCESSED;

        LOG.infof("EdiProcessor [COPRAR] %s: %s", msg.id, msg.processingNote);
    }

    /**
     * Handle a CUSCAR message: update customs status of existing containers.
     */
    private void processCuscar(EdiMessage msg) {
        List<EdiMessageMapper.CuscarUpdate> updates = mapper.parseCuscar(msg.rawPayload);

        int count = 0;
        for (EdiMessageMapper.CuscarUpdate update : updates) {
            if (update.containerNumber == null) {
                continue;
            }

            Item item = Item.find("containerNumber", update.containerNumber).firstResult();
            if (item == null) {
                LOG.warnf("EdiProcessor [CUSCAR] %s: container %s not found — skipping",
                        msg.id, update.containerNumber);
                continue;
            }

            // Apply customs update fields
            if (update.customsStatus != null) {
                item.setCustomsStatus(parseCustomsStatus(update.customsStatus));
            }
            item.setHsCode(update.hsCode);
            item.setCountryOfOrigin(update.countryOfOrigin);
            item.update();
            count++;
        }

        msg.processingNote = "Updated " + count + " containers";
        msg.status = EdiMessage.EdiStatus.PROCESSED;

        LOG.infof("EdiProcessor [CUSCAR] %s: %s", msg.id, msg.processingNote);
    }

    // -------------------------------------------------------------------------
    // Phase P1 message handlers — metadata only, business routing via events
    // -------------------------------------------------------------------------

    /**
     * CODECO — container status information from the ocean carrier.
     *
     * <p>Today we acknowledge receipt and let listeners of
     * {@code edi.MessageIngested} decide how to react. Full parsing (container
     * number, status code, timestamp, equipment type) lands with the Yard
     * listener that auto-allocates on discharge.
     */
    private void processCodeco(EdiMessage msg) {
        msg.processingNote = "CODECO received — parser to be completed (container status updates)";
        msg.status = EdiMessage.EdiStatus.PROCESSED;
        LOG.infof("EdiProcessor [CODECO] %s: received, pending detailed parsing", msg.id);
    }

    /**
     * COPARN — booking / container release order.
     *
     * <p>Creates or updates a {@link fr.alb.bol.model.BillOfLading} reservation.
     * For now we log receipt; the BOL context will add the specific
     * {@code bol.BolRegistered} listener when the full parser lands.
     */
    private void processCoparn(EdiMessage msg) {
        msg.processingNote = "COPARN received — booking/release payload queued for BOL context";
        msg.status = EdiMessage.EdiStatus.PROCESSED;
        LOG.infof("EdiProcessor [COPARN] %s: received, pending detailed parsing", msg.id);
    }

    /**
     * BAPLIE — bay plan (stowage) for a vessel call. Large payload with one
     * line per container and its stowage position.
     *
     * <p>Will feed {@code berth} and {@code yard} to drive load/discharge
     * sequences. Today we acknowledge reception.
     */
    private void processBaplie(EdiMessage msg) {
        msg.processingNote = "BAPLIE received — stowage plan queued for berth/yard contexts";
        msg.status = EdiMessage.EdiStatus.PROCESSED;
        LOG.infof("EdiProcessor [BAPLIE] %s: received, pending detailed parsing", msg.id);
    }

    /**
     * MOVINS — move instructions issued by the carrier for a vessel call.
     */
    private void processMovins(EdiMessage msg) {
        msg.processingNote = "MOVINS received — move instructions queued for yard context";
        msg.status = EdiMessage.EdiStatus.PROCESSED;
        LOG.infof("EdiProcessor [MOVINS] %s: received, pending detailed parsing", msg.id);
    }

    /**
     * CALINF — vessel call information (arrival / departure notice).
     *
     * <p>Will be translated into a {@code berth.BerthAllocation} hint. Today
     * we acknowledge reception.
     */
    private void processCalinf(EdiMessage msg) {
        msg.processingNote = "CALINF received — vessel call info queued for berth context";
        msg.status = EdiMessage.EdiStatus.PROCESSED;
        LOG.infof("EdiProcessor [CALINF] %s: received, pending detailed parsing", msg.id);
    }

    // -------------------------------------------------------------------------

    /**
     * Safely parse a customs status string, falling back to PENDING on unknown values.
     */
    private static fr.alb.type.CustomsStatus parseCustomsStatus(String raw) {
        try {
            return fr.alb.type.CustomsStatus.fromValue(raw);
        } catch (IllegalArgumentException e) {
            try {
                return fr.alb.type.CustomsStatus.valueOf(raw.toUpperCase());
            } catch (IllegalArgumentException ex) {
                LOG.warnf("EdiProcessor: unknown CustomsStatus '%s' — defaulting to PENDING", raw);
                return fr.alb.type.CustomsStatus.PENDING;
            }
        }
    }
}
