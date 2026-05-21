package fr.alb.billing.domain;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persistent audit record of a single charge calculation.
 *
 * Created every time a charge is computed. Linked to an invoice once aggregated.
 * Provides full traceability: who computed it, with what inputs, which contract,
 * which rate, and the human-readable explanation.
 *
 * Future: these records become the source of truth for dispute resolution.
 */
@MongoEntity(collection = "CHARGE_RECORD")
public class ChargeRecord extends PanacheMongoEntityBase {

    @BsonId
    public ObjectId id;

    // --- Business keys ---
    public String itemId;
    public String contractId;
    public String contractName;
    public String rateId;
    public String invoiceId;           // null until the record is aggregated into an invoice

    // --- Computed values ---
    public BigDecimal amount;
    public BigDecimal quantity;
    public String uom;
    public String currency;

    // --- Audit trail ---
    public String calculatorUsed;          // e.g. "DateByTeuCalculator"
    public Map<String, Object> inputs;     // all inputs used: inDate, outDate, days, unitPrice…
    public String explanation;             // "5 days × EUR 12.00/day = EUR 60.00"

    // --- Lifecycle ---
    public String status;                  // PENDING, INVOICED, CANCELLED
    public Instant calculatedAt;
    public String calculatedBy;            // "SYSTEM" or username

    // --- Soft delete ---
    public boolean deleted = false;
    public Instant deletedAt;

    // --- Static finders ---

    public static List<ChargeRecord> findByItem(String itemId) {
        return list("itemId", itemId);
    }

    public static List<ChargeRecord> findByInvoice(String invoiceId) {
        return list("invoiceId", invoiceId);
    }

    public static List<ChargeRecord> findPending() {
        return list("status = ?1 and deleted = ?2", "PENDING", false);
    }

    /** Factory: create from a ChargeResult */
    public static ChargeRecord from(ChargeResult result, String itemId, String contractName, String calculatedBy) {
        ChargeRecord rec = new ChargeRecord();
        rec.itemId         = itemId;
        rec.contractId     = result.contractId();
        rec.contractName   = contractName;
        rec.rateId         = result.rateId();
        rec.amount         = result.amount();
        rec.quantity       = result.quantity();
        rec.uom            = result.uom();
        rec.currency       = result.currency();
        rec.calculatorUsed = result.calculatorUsed();
        rec.inputs         = result.inputs();
        rec.explanation    = result.explanation();
        rec.status         = "PENDING";
        rec.calculatedAt   = Instant.now();
        rec.calculatedBy   = calculatedBy != null ? calculatedBy : "SYSTEM";
        rec.deleted        = false;
        return rec;
    }
}
