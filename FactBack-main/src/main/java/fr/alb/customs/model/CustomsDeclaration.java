package fr.alb.customs.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Customs declaration — the contract with the port's customs authority
 * that one or more containers are cleared to leave (import) or enter
 * (export) the bonded area.
 *
 * <p>Lifecycle: {@code DRAFT → SUBMITTED → (HELD) → CLEARED | REJECTED}.
 * Gate-out is blocked while the covering declaration is not
 * {@link Status#CLEARED} — the gate context consults
 * {@code fr.alb.customs.api.CustomsClearanceChecker} before issuing any
 * outbound gate event.
 */
@MongoEntity(collection = "CUSTOMS_DECLARATION")
public class CustomsDeclaration extends EntityBase {

    private static final long serialVersionUID = 1L;

    public enum Status {
        /** Local draft — not yet sent to customs. */
        DRAFT,
        /** Sent to customs authority, awaiting decision. */
        SUBMITTED,
        /** Customs has paused clearance (inspection, missing documents…). */
        HELD,
        /** Goods are free to move. */
        CLEARED,
        /** Permanently refused — requires a new declaration to retry. */
        REJECTED
    }

    public enum DeclarationType { IMPORT, EXPORT, TRANSIT }

    public DeclarationType type = DeclarationType.IMPORT;
    public Status status = Status.DRAFT;

    /** Master / House BOL covered by this declaration. */
    public String billOfLadingId;

    /** Items (containers) on the declaration. Can be a subset of the BOL. */
    public List<String> itemIds = new ArrayList<>();

    /** Declarant (customs broker or importer of record). */
    public String declarantName;
    public String declarantTaxId;

    /** Customs reference assigned once the declaration is accepted. */
    public String declarationReference;

    /** Port / customs office code (UN/LOCODE or local code). */
    public String portOfEntryCode;

    /** Cargo value declared for duty calculation. */
    public BigDecimal totalDeclaredValue;
    public String currency;

    /** Customs charges assessed by the authority (duties + VAT). */
    public BigDecimal assessedDuties;

    public Instant submittedAt;
    public Instant heldAt;
    public String holdReason;
    public Instant clearedAt;
    public Instant rejectedAt;
    public String rejectionReason;

    /** Free-form notes (inspection outcome, amendment history, etc.). */
    public String notes;
}
