package fr.alb.infrastructure.db;

import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import fr.alb.billing.domain.ChargeRecord;
import fr.alb.infrastructure.outbox.OutboxEvent;
import fr.alb.bol.model.BillOfLading;
import fr.alb.dd.model.DdAccrual;
import fr.alb.dd.model.DdRule;
import fr.alb.edi.model.EdiMessage;
import fr.alb.ai.featurerequest.model.FeatureRequest;
import fr.alb.billing.model.Invoice;
import fr.alb.yard.model.Item;
import fr.alb.yard.model.ItemEvent;
import fr.alb.yard.model.Lifecycle;
import fr.alb.billing.model.Payment;
import fr.alb.billing.model.Tariff;
import fr.alb.parties.model.ThirdParty;
import fr.alb.model.Asset;
import fr.alb.billing.model.InvoiceTemplate;
import fr.alb.berth.model.Vessel;
import fr.alb.ais.model.VesselAisSnapshot;
import fr.alb.equipment.model.ContainerArchetype;
import fr.alb.equipment.model.IsoContainerCode;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.quarkus.runtime.StartupEvent;
import org.bson.Document;
import org.jboss.logging.Logger;

/**
 * Ensures all MongoDB indexes exist at startup.
 * Centralizes index management — add new indexes here instead of scattering
 * @PostConstruct across DAOs.
 *
 * Safe to run on every startup: createIndex() is idempotent in MongoDB.
 */
@ApplicationScoped
public class IndexInitializer {

    private static final Logger LOGGER = Logger.getLogger(IndexInitializer.class);

    // Lower priority value = earlier execution. We must run BEFORE any seeder
    // (e.g. IsoContainerCodeSeeder uses APPLICATION+1000) because Cosmos DB
    // refuses to create a unique index on a non-empty collection.
    void onStart(@Observes @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION) StartupEvent ev) {
        ensureItemEventIndexes();
        ensureItemIndexes();
        ensureLifecycleIndexes();
        ensureOutboxEventIndexes();
        ensureChargeRecordIndexes();
        ensureTariffIndexes();
        ensureEdiMessageIndexes();
        ensurePaymentIndexes();
        ensureBolIndexes();
        ensureInvoiceIndexes();
        ensureThirdPartyIndexes();
        ensureDdRuleIndexes();
        ensureDdAccrualIndexes();
        ensureFeatureRequestIndexes();
        ensureInvoiceTemplateIndexes();
        ensureAssetIndexes();
        ensureVesselMmsiIndex();
        ensureVesselAisSnapshotIndexes();
        ensureIsoContainerCodeIndexes();
        ensureContainerArchetypeIndexes();
    }

    private void ensureItemEventIndexes() {
        try {
            MongoCollection<Document> col = ItemEvent.mongoCollection().withDocumentClass(Document.class);
            // Heavily queried: getItemEvents() filters by itemId
            col.createIndex(Indexes.ascending("itemId"));
            // Used in getEventType() — EventConfig lookup by eventId
            col.createIndex(Indexes.ascending("eventId"));
            LOGGER.debug("IndexInitializer: ItemEvent indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating ItemEvent indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create ItemEvent indexes");
        }
    }

    private void ensureItemIndexes() {
        try {
            MongoCollection<Document> col = Item.mongoCollection().withDocumentClass(Document.class);
            col.createIndex(Indexes.ascending("containerNumber"));
            col.createIndex(Indexes.compoundIndex(Indexes.ascending("billOfLadingId"), Indexes.ascending("emptyStatus")));
            col.createIndex(Indexes.ascending("customsStatus"));
            col.createIndex(Indexes.ascending("hazmatFlag"));
            col.createIndex(Indexes.ascending("gateInDate"));
            col.createIndex(Indexes.ascending("ownerId"));
            LOGGER.debug("IndexInitializer: Item indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating Item indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create Item indexes");
        }
    }

    private void ensureLifecycleIndexes() {
        try {
            MongoCollection<Document> col = Lifecycle.mongoCollection().withDocumentClass(Document.class);
            // Used in ItemEventResource: find("itemId = ?1 and status = ?2")
            col.createIndex(Indexes.compoundIndex(
                Indexes.ascending("itemId"),
                Indexes.ascending("status")
            ));
            // Fallback find by itemId alone
            col.createIndex(Indexes.ascending("itemId"));
            LOGGER.debug("IndexInitializer: Lifecycle indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating Lifecycle indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create Lifecycle indexes");
        }
    }

    private void ensureOutboxEventIndexes() {
        try {
            MongoCollection<Document> col = OutboxEvent.mongoCollection().withDocumentClass(Document.class);
            // OutboxScheduler polls by status = PENDING
            col.createIndex(Indexes.ascending("status"));
            // TTL index: auto-delete events after 30 days
            // Note: Cosmos DB does not support partialFilterExpression on TTL indexes
            col.createIndex(
                Indexes.ascending("processedAt"),
                new IndexOptions().expireAfter(30L, java.util.concurrent.TimeUnit.DAYS)
            );
            LOGGER.debug("IndexInitializer: OutboxEvent indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating OutboxEvent indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create OutboxEvent indexes");
        }
    }

    private void ensureChargeRecordIndexes() {
        try {
            MongoCollection<Document> col = ChargeRecord.mongoCollection().withDocumentClass(Document.class);
            col.createIndex(Indexes.ascending("itemId"));
            col.createIndex(Indexes.ascending("invoiceId"));
            col.createIndex(Indexes.compoundIndex(
                Indexes.ascending("itemId"),
                Indexes.ascending("status")
            ));
            LOGGER.debug("IndexInitializer: ChargeRecord indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating ChargeRecord indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create ChargeRecord indexes");
        }
    }

    private void ensureTariffIndexes() {
        try {
            MongoCollection<Document> col = Tariff.mongoCollection().withDocumentClass(Document.class);
            // TariffResource: GET /tariffs?serviceType=STORAGE
            col.createIndex(Indexes.ascending("serviceType"));
            // getActiveTariffs: filter by status
            col.createIndex(Indexes.ascending("status"));
            LOGGER.debug("IndexInitializer: Tariff indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating Tariff indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create Tariff indexes");
        }
    }

    private void ensureEdiMessageIndexes() {
        try {
            MongoCollection<Document> col = EdiMessage.mongoCollection().withDocumentClass(Document.class);
            // EdiResource: list by status
            col.createIndex(Indexes.ascending("status"));
            // Filter by partner
            col.createIndex(Indexes.ascending("partnerId"));
            // TTL: auto-delete messages after 90 days
            // Note: Cosmos DB does not support partialFilterExpression on TTL indexes
            col.createIndex(
                Indexes.ascending("processedAt"),
                new IndexOptions().expireAfter(90L, java.util.concurrent.TimeUnit.DAYS)
            );
            LOGGER.debug("IndexInitializer: EdiMessage indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating EdiMessage indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create EdiMessage indexes");
        }
    }

    private void ensurePaymentIndexes() {
        try {
            MongoCollection<Document> col = Payment.mongoCollection().withDocumentClass(Document.class);
            // PaymentResource: listByCustomer
            col.createIndex(Indexes.ascending("customerId"));
            // PaymentResource: listByStatus
            col.createIndex(Indexes.ascending("status"));
            // Compound index for customer history sorted by payment date descending
            col.createIndex(Indexes.compoundIndex(
                Indexes.ascending("customerId"),
                Indexes.descending("paymentDate")
            ));
            // Unique index on human-readable payment reference (sparse to allow nulls during ingestion)
            col.createIndex(Indexes.ascending("paymentReference"),
                new IndexOptions().unique(true).sparse(true));
            // Lookup by bank transaction reference
            col.createIndex(Indexes.ascending("bankReference"));
            LOGGER.debug("IndexInitializer: Payment indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating Payment indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create Payment indexes");
        }
    }

    private void ensureBolIndexes() {
        try {
            MongoCollection<Document> col = BillOfLading.mongoCollection().withDocumentClass(Document.class);
            // Lookup by human-readable BOL number
            col.createIndex(Indexes.ascending("blNumber"));
            // Vessel schedule queries
            col.createIndex(Indexes.compoundIndex(
                Indexes.ascending("vessel"),
                Indexes.ascending("voyage")
            ));
            // Booking reference lookup
            col.createIndex(Indexes.ascending("bookingNumber"));
            // Filter by BOL lifecycle status
            col.createIndex(Indexes.ascending("bolStatus"));
            // Filter by carrier
            col.createIndex(Indexes.ascending("shippingLine"));
            LOGGER.debug("IndexInitializer: BillOfLading indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating BillOfLading indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create BillOfLading indexes");
        }
    }

    private void ensureInvoiceIndexes() {
        try {
            MongoCollection<Document> col = Invoice.mongoCollection().withDocumentClass(Document.class);
            // Aging/receivables: customer invoices sorted by issue date descending
            col.createIndex(Indexes.compoundIndex(
                Indexes.ascending("customerKey"),
                Indexes.descending("invoiceDate")
            ));
            // Overdue tracking: filter unpaid invoices by due date
            col.createIndex(Indexes.compoundIndex(
                Indexes.ascending("paymentStatus"),
                Indexes.ascending("dueDate")
            ));
            // Contract-based invoice lookup
            col.createIndex(Indexes.ascending("billingContractId"));
            // Customer PO reference lookup
            col.createIndex(Indexes.ascending("poNumber"));
            LOGGER.debug("IndexInitializer: Invoice indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating Invoice indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create Invoice indexes");
        }
    }

    private void ensureThirdPartyIndexes() {
        try {
            MongoCollection<Document> col = ThirdParty.mongoCollection().withDocumentClass(Document.class);
            // Tax/VAT number lookup for invoice compliance
            col.createIndex(Indexes.ascending("taxIdentificationNumber"));
            // EDI partner routing lookup
            col.createIndex(Indexes.ascending("ediPartnerCode"));
            // Filter customers by type and status (e.g. active shippers)
            col.createIndex(Indexes.compoundIndex(
                Indexes.ascending("customerType"),
                Indexes.ascending("status")
            ));
            LOGGER.debug("IndexInitializer: ThirdParty indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating ThirdParty indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create ThirdParty indexes");
        }
    }

    private void ensureDdRuleIndexes() {
        try {
            MongoCollection<Document> col = DdRule.mongoCollection().withDocumentClass(Document.class);
            // Filter rules by type (DEMURRAGE / DETENTION)
            col.createIndex(Indexes.ascending("ddType"));
            // Filter rules by carrier
            col.createIndex(Indexes.ascending("carrierId"));
            // Resolver lookup key: find the most specific rule for a carrier + container type
            col.createIndex(Indexes.compoundIndex(
                Indexes.ascending("ddType"),
                Indexes.ascending("carrierId"),
                Indexes.ascending("containerTypeCode")
            ));
            // Filter by lifecycle status
            col.createIndex(Indexes.ascending("status"));
            LOGGER.debug("IndexInitializer: DdRule indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating DdRule indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create DdRule indexes");
        }
    }

    private void ensureDdAccrualIndexes() {
        try {
            MongoCollection<Document> col = DdAccrual.mongoCollection().withDocumentClass(Document.class);
            // One accrual per container per D&D type
            col.createIndex(
                Indexes.compoundIndex(Indexes.ascending("itemId"), Indexes.ascending("ddType")),
                new IndexOptions().unique(true)
            );
            // Filter accruals by lifecycle status (e.g. RUNNING)
            col.createIndex(Indexes.ascending("status"));
            // Filter accruals by carrier
            col.createIndex(Indexes.ascending("carrierId"));
            // Sort / range queries by clock start date
            col.createIndex(Indexes.ascending("clockStart"));
            LOGGER.debug("IndexInitializer: DdAccrual indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating DdAccrual indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create DdAccrual indexes");
        }
    }

    private void ensureFeatureRequestIndexes() {
        try {
            MongoCollection<Document> col = FeatureRequest.mongoCollection().withDocumentClass(Document.class);
            // Filter by lifecycle status
            col.createIndex(Indexes.ascending("status"));
            // Filter by submitter (non-admin users see only their own)
            col.createIndex(Indexes.ascending("createdBy"));
            // Backlog query: status + priority DESC for ordered backlog view
            col.createIndex(Indexes.compoundIndex(
                Indexes.ascending("status"),
                Indexes.descending("priority")
            ));
            // Filter by clarification phase
            col.createIndex(Indexes.ascending("clarificationsDone"));
            // Ticketing indexes
            col.createIndex(Indexes.ascending("ticketNumber"), new IndexOptions().unique(true).sparse(true));
            col.createIndex(Indexes.ascending("assignedTo"));
            col.createIndex(Indexes.ascending("milestone"));
            col.createIndex(Indexes.ascending("category"));
            LOGGER.debug("IndexInitializer: FeatureRequest indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating FeatureRequest indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create FeatureRequest indexes");
        }
    }

    private void ensureInvoiceTemplateIndexes() {
        try {
            MongoCollection<Document> col = InvoiceTemplate.mongoCollection().withDocumentClass(Document.class);
            col.createIndex(Indexes.ascending("name"));
            col.createIndex(Indexes.descending("updatedAt"));
            LOGGER.debug("IndexInitializer: InvoiceTemplate indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating InvoiceTemplate indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create InvoiceTemplate indexes");
        }
    }

    private void ensureAssetIndexes() {
        try {
            MongoCollection<Document> col = Asset.mongoCollection().withDocumentClass(Document.class);
            col.createIndex(Indexes.ascending("name"));
            col.createIndex(Indexes.descending("createdAt"));
            LOGGER.debug("IndexInitializer: Asset indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating Asset indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create Asset indexes");
        }
    }

    private void ensureVesselMmsiIndex() {
        try {
            MongoCollection<Document> col = Vessel.mongoCollection().withDocumentClass(Document.class);
            col.createIndex(Indexes.ascending("mmsi"), new IndexOptions().sparse(true));
            LOGGER.debug("IndexInitializer: Vessel.mmsi index OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating Vessel.mmsi index — continuing without it");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create Vessel.mmsi index");
        }
    }

    private void ensureVesselAisSnapshotIndexes() {
        try {
            MongoCollection<Document> col =
                VesselAisSnapshot.mongoCollection().withDocumentClass(Document.class);
            // sparse so a partial document without mmsi doesn't conflict with a real null entry;
            // changing the TTL below requires manually dropping + recreating the index in Mongo.
            col.createIndex(Indexes.ascending("mmsi"), new IndexOptions().unique(true).sparse(true));
            col.createIndex(
                Indexes.ascending("lastSeen"),
                new IndexOptions().expireAfter(24L, java.util.concurrent.TimeUnit.HOURS)
            );
            LOGGER.debug("IndexInitializer: VesselAisSnapshot indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating VesselAisSnapshot indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create VesselAisSnapshot indexes");
        }
    }

    private void ensureIsoContainerCodeIndexes() {
        try {
            MongoCollection<Document> col =
                IsoContainerCode.mongoCollection().withDocumentClass(Document.class);
            col.createIndex(Indexes.ascending("code"), new IndexOptions().unique(true));
            col.createIndex(Indexes.ascending("archetypeId"), new IndexOptions().sparse(true));
            col.createIndex(Indexes.ascending("isActive"));
            LOGGER.debug("IndexInitializer: IsoContainerCode indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating IsoContainerCode indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create IsoContainerCode indexes");
        }
    }

    private void ensureContainerArchetypeIndexes() {
        try {
            MongoCollection<Document> col =
                ContainerArchetype.mongoCollection().withDocumentClass(Document.class);
            col.createIndex(Indexes.ascending("code"), new IndexOptions().unique(true).sparse(true));
            col.createIndex(Indexes.ascending("isActive"));
            LOGGER.debug("IndexInitializer: ContainerArchetype indexes OK");
        } catch (MongoTimeoutException e) {
            LOGGER.warn("IndexInitializer: timeout creating ContainerArchetype indexes — continuing without them");
        } catch (MongoException e) {
            LOGGER.errorf(e, "IndexInitializer: failed to create ContainerArchetype indexes");
        }
    }
}
