package fr.alb.billing.dao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import fr.alb.model.*;
import fr.alb.billing.model.*;
import fr.alb.billing.domain.*;
import fr.alb.yard.model.Item;
import fr.alb.dao.ItemDao;
import fr.alb.yard.model.ItemEvent;
import fr.alb.yard.model.Lifecycle;
import fr.alb.yard.model.EventConfig;
import fr.alb.yard.model.Commodity;
import fr.alb.bol.model.BillOfLading;
import fr.alb.dd.model.DdRule;
import fr.alb.dd.model.DdAccrual;
import fr.alb.dd.model.DdWaiver;
import fr.alb.dd.model.HolidayCalendar;
import fr.alb.type.Status;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jboss.logging.Logger;

import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Facet;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;

import fr.alb.billing.domain.ChargeRecord;
import fr.alb.billing.domain.ChargeResult;
import fr.alb.billing.event.InvoiceFinalized;
import fr.alb.billing.service.InvoiceCalculationService;
import fr.alb.billing.service.RateSelectionService;
import fr.alb.platform.event.DomainEventPublisher;
import fr.alb.type.CalculationModeType;
import fr.alb.type.CalculationSubType;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class InvoiceDaoImpl implements InvoiceDao {

        private static final Logger LOGGER = Logger.getLogger(InvoiceDaoImpl.class);

        @Inject
        ItemDao itemDao;

    @Inject
    ContractDao contractDao;

    @Inject
    fr.alb.billing.service.InvoiceLinePipeline invoiceLinePipeline;

    @Inject
    fr.alb.billing.service.InvoiceNumberService invoiceNumberService;

    @Inject
    InvoiceCalculationService invoiceCalculationService;

    @Inject
    RateSelectionService rateSelectionService;

    @Inject
    DomainEventPublisher domainEvents;

    @ConfigProperty(name = "app.timezone", defaultValue = "Europe/Paris")
    String timezone = "Europe/Paris";

    @PostConstruct
    void ensureIndexes() {
                try {
                        MongoCollection<Document> collection = Invoice.mongoCollection().withDocumentClass(Document.class);
                        collection.createIndex(Indexes.compoundIndex(
                                        Indexes.ascending("customerKey"),
                                        Indexes.ascending("facilityKey"),
                                        Indexes.ascending("status"),
                                        Indexes.descending("createdDate")));
                        collection.createIndex(Indexes.compoundIndex(
                                        Indexes.ascending("customerKey"),
                                        Indexes.ascending("facilityKey"),
                                        Indexes.ascending("status"),
                                        Indexes.descending("amount")));
                        collection.createIndex(Indexes.ascending("draftNumber"));
                        collection.createIndex(Indexes.ascending("finalNumber"));
                } catch (MongoTimeoutException e) {
                        LOGGER.error("Timed out while creating indexes for Invoice collection; the application will continue without them", e);
                } catch (MongoException e) {
                        String message = "Failed to create indexes for Invoice collection";
                        LOGGER.error(message, e);
                        throw new IllegalStateException(message, e);
                }
    }

    private ZoneId zone() {
        return ZoneId.of(timezone);
    }

    // -------------------------------------------------------------------------
    // Delegation bridges — kept so existing callers (InvoiceLinePipeline,
    // InvoiceComputationService) continue to compile without changes.
    // TODO: update those callers to inject InvoiceCalculationService /
    //       RateSelectionService directly and remove these bridge methods.
    // -------------------------------------------------------------------------

    /** @deprecated Inject {@link InvoiceCalculationService} and call it directly. */
    @Deprecated
    public List<Event> getItemEvents(Item item) {
        return invoiceCalculationService.getItemEvents(item);
    }

    /** @deprecated Inject {@link InvoiceCalculationService} and call it directly. */
    @Deprecated
    public Event findSingleInEvent(Item item) {
        return invoiceCalculationService.findSingleInEvent(item);
    }

    /** @deprecated Inject {@link RateSelectionService} and call it directly. */
    @Deprecated
    public RateManagement selectRate(List<RateManagement> rates, LocalDate date, String currency, String uom) {
        return rateSelectionService.selectRate(rates, date, currency, uom);
    }

    // -------------------------------------------------------------------------
    // InvoiceDao interface — persistence methods
    // -------------------------------------------------------------------------

       @Override
       public java.util.Optional<Invoice> makeInvoice(List<String> itemsId, String customer, String billOfLadingId) {
            // Idempotency: return existing draft if already created for these items+customer
            String idempotencyKey = computeIdempotencyKey(itemsId, customer);
            Optional<Invoice> existingDraft = Invoice.find("idempotencyKey", idempotencyKey).firstResultOptional();
            if (existingDraft.isPresent()) {
                LOGGER.infof("makeInvoice: returning existing draft for idempotencyKey=%s", idempotencyKey);
                return existingDraft;
            }

            // Get the list of active contracts
            List<Contract> activeContracts = contractDao.findActiveContracts();

            LOGGER.debugf("--------- Active contracts found: %d", activeContracts.size());

            // Bulk fetch all items in a single query (avoids N+1)
            List<Item> items = Item.find("_id in ?1", itemsId).list();
            Map<String, Item> itemMap = items.stream()
                    .collect(Collectors.toMap(i -> i.getId(), i -> i));

            // Check for already invoiced items to prevent duplicates
            for (Item existing : items) {
                if (existing.getRelatedInvoice() != null) {
                    String invId = existing.getRelatedInvoice();
                    LOGGER.debugf("[Invoice] Customer=%s — Invoice already exists (id=%s) for item %s, skipping draft generation",
                            customer, invId, existing.getId());
                    throw new IllegalStateException("Invoice already exists: " + invId);
                }
            }

            // Create a new invoice
            Invoice invoice = new Invoice();
            invoice.createdDate = LocalDate.now(zone());
            invoice.customerName = customer;
            invoice.customerKey = customer == null ? null : customer.trim().toUpperCase();
            invoice.itemIds = new ArrayList<>(itemsId);
            if (billOfLadingId != null) {
                invoice.billOfLadingId = billOfLadingId;
            }
            BigDecimal totalAmount = BigDecimal.ZERO;

            // Bulk fetch all events for all items in 3 queries (avoids N+1)
            Map<String, List<Event>> eventsPerItem = invoiceCalculationService.getItemEventsMap(items);

            // Map to track which events have been billed to avoid double billing
            Map<String, Set<String>> billedEvents = new HashMap<>();
            int lineCount = 0;

            for (String itemId : itemsId) {
                Item item = itemMap.get(itemId);
                if (item == null) {
                    LOGGER.debugf("Item not found: %s", itemId);
                    continue;
                }

                List<Event> events = eventsPerItem.getOrDefault(itemId, List.of());
                for (Event event : events) {
                    for (Contract contract : activeContracts) {
                        String eventKey = itemId + "|" + event.getType() + "|" + event.getTimeStamp();
                        if (!billedEvents.computeIfAbsent(itemId, k -> new HashSet<>()).contains(eventKey)) {
                            ChargeResult chargeResult = invoiceCalculationService.calculateCharge(contract, item, event);
                            BigDecimal amount = chargeResult.amount();
                            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                                lineCount++;
                                try {
                                    ChargeRecord chargeRecord = ChargeRecord.from(chargeResult, itemId, contract.name, "SYSTEM");
                                    chargeRecord.persist();
                                } catch (Exception e) {
                                    LOGGER.warnf("Failed to persist ChargeRecord for item %s: %s", itemId, e.getMessage());
                                }
                            }
                            totalAmount = totalAmount.add(amount);
                            billedEvents.get(itemId).add(eventKey);

                            LOGGER.debugf("Billed event: Item %s, Event %s at %s - Amount: %s %s",
                                itemId, event.getType(), event.getTimeStamp(),
                                amount,
                                contract.rates.isEmpty() ? "" : contract.rates.get(0).getCurrency());
                        }
                    }
                }
            }

            if (lineCount == 0) {
                LOGGER.debugf("[Invoice] Customer=%s — No lines generated, draft invoice will NOT be created", customer);
                return java.util.Optional.empty();
            }

            // Set the total amount and save the invoice
            LOGGER.debugf("amount ------- %s", totalAmount);
            invoice.amount = totalAmount.doubleValue();
            invoice.draftNumber = invoiceNumberService.generateDraftNumber();
            invoice.idempotencyKey = idempotencyKey;

            // === Wire paymentTerms + dueDate from ThirdParty (spec 2026-05-02) ===
            String tpTerms = null;
            if (invoice.customerKey != null && !invoice.customerKey.isEmpty()) {
                fr.alb.parties.model.ThirdParty tp = fr.alb.parties.model.ThirdParty
                    .find("customerCode = ?1 or _id = ?1", invoice.customerKey)
                    .firstResult();
                if (tp != null) {
                    tpTerms = tp.getPaymentTermsDefault();
                } else {
                    LOGGER.warnf("makeInvoice: no ThirdParty found for customerKey=%s, using default NET30", invoice.customerKey);
                }
            } else {
                LOGGER.warnf("makeInvoice: invoice has no customerKey, using default NET30");
            }
            int days = fr.alb.billing.util.PaymentTermsParser.parseDays(tpTerms);
            invoice.setPaymentTerms("NET" + days);
            invoice.setDueDate(invoice.createdDate.plusDays(days));

            // === Bind the active 'draft' InvoiceTemplate (drafts always start as drafts) ===
            // Backend enforces "at most one active per type", so firstResult() without sort is deterministic.
            // Falls back to any active template if no draft-specific one is set.
            // (Cosmos Mongo API rejects $sort on non-indexed fields, so no Sort here.)
            fr.alb.billing.model.InvoiceTemplate draftTemplate = fr.alb.billing.model.InvoiceTemplate
                    .<fr.alb.billing.model.InvoiceTemplate>find("status = ?1 and type = ?2", "active", "draft")
                    .firstResult();
            if (draftTemplate == null) {
                draftTemplate = fr.alb.billing.model.InvoiceTemplate
                        .<fr.alb.billing.model.InvoiceTemplate>find("status = ?1", "active")
                        .firstResult();
            }
            if (draftTemplate != null) {
                invoice.templateId = draftTemplate.id;
            } else {
                LOGGER.warnf("makeInvoice: no active InvoiceTemplate found, invoice will fall back to default Qute template");
            }

            invoice.persist();

           // Update each item using the already-fetched itemMap (no extra queries)
           List<String> failedItems = new ArrayList<>();
           for (String itemId : itemsId) {
               Item item = itemMap.get(itemId);
               if (item == null) {
                   LOGGER.debugf("Item not found: %s", itemId);
                   failedItems.add(itemId);
                   continue;
               }

                item.setRelatedInvoice(invoice.id);
                try {
                    item.update();
                } catch (Exception e) {
                    LOGGER.errorf(e, "Failed to update item %s for invoice %s", itemId, invoice.id);
                    failedItems.add(itemId);
                }
            }

           if (!failedItems.isEmpty()) {
               LOGGER.warnf("Invoice %s created, but the following items failed to update: %s", invoice.id, failedItems);
           }

          LOGGER.debugf("Invoice created with total amount: %s", totalAmount);
          return java.util.Optional.of(invoice);
       }

       @Override
       public BigDecimal generateInvoiceByBillOfLading(String billOfLadingId, LocalDate invoiceDate) {
           // Fetch items linked to the Bill of Lading directly from the database
           List<Item> items = itemDao.getItemsByBillOfLadingId(billOfLadingId);
           if (items.isEmpty()) {
               throw new IllegalArgumentException("Bill of Lading not found or has no items");
           }
           if (LOGGER.isDebugEnabled()) {
               LOGGER.debugf("[InvoiceCalc] Generating invoice for BOL=%s, invoiceDate=%s, items=%d", billOfLadingId,
                       invoiceDate, items.size());
           }

           // Prevent duplicates: if any item already linked to an invoice, abort
           for (Item item : items) {
               if (item.getRelatedInvoice() != null) {
                   LOGGER.debugf("[Invoice] BOL=%s — Invoice already exists (id=%s), skipping draft generation", billOfLadingId,
                           item.getRelatedInvoice());
                   throw new IllegalStateException("Invoice already exists: " + item.getRelatedInvoice());
               }
           }

           List<Contract> activeContracts = contractDao.findActiveContracts();
           BigDecimal total = BigDecimal.ZERO;
           int lineCount = 0;

           LocalDate effectiveDate = invoiceDate != null ? invoiceDate : LocalDate.now(zone());

           for (Item item : items) {
               if (LOGGER.isDebugEnabled()) {
                   LOGGER.debugf("[InvoiceCalc] Processing Item=%s (BOL=%s)", item.getId(), item.getBillOfLadingId());
               }

               BigDecimal itemTotal = BigDecimal.ZERO;

               for (Contract contract : activeContracts) {
                   if (contract.calculationMode == null || contract.calculationMode.type != CalculationModeType.DATE) {
                       continue;
                   }
                   if (CalculationSubType.from(contract.calculationMode.subType) != CalculationSubType.IN_DATE) {
                       continue;
                   }

                   if (LOGGER.isDebugEnabled()) {
                       LOGGER.debugf("[InvoiceCalc] Contract=%s mode=%s/%s", contract.getId(),
                               contract.calculationMode.type, contract.calculationMode.subType);
                   }

                   Event inEvent = invoiceCalculationService.findSingleInEvent(item);
                   if (inEvent == null) {
                       if (LOGGER.isDebugEnabled()) {
                           LOGGER.debugf("[InvoiceCalc] Item %s has no IN event - skipping", item.getId());
                       }
                       continue; // no IN event
                   }

                   LocalDate inDate = inEvent.getTimeStamp().toInstant().atZone(zone()).toLocalDate();
                   long days = ChronoUnit.DAYS.between(inDate, effectiveDate);
                   if (days <= 0) {
                       if (LOGGER.isDebugEnabled()) {
                           LOGGER.debugf(
                                   "[InvoiceCalc] Non-positive day count for Item %s: IN=%s InvoiceDate=%s → days=%d - skipping",
                                   item.getId(), inDate, effectiveDate, days);
                       }
                       continue;
                   }

                  RateManagement rate = rateSelectionService.selectRate(contract.rates, effectiveDate, null, "DAY",
                      item.getCategory() != null ? item.getCategory().getValue() : null,
                      item.getFreightKind() != null ? item.getFreightKind().getValue() : null);
                  if (rate == null) {
                      if (LOGGER.isDebugEnabled()) {
                          LOGGER.debugf("[InvoiceCalc] No applicable rate for contract %s on %s (uom=DAY). Check currency/UoM/date windows.",
                                  contract.getId(), effectiveDate);
                      }
                      continue;
                  }

                   BigDecimal amount = BigDecimal.valueOf(rate.getAmount()).multiply(BigDecimal.valueOf(days));
                   itemTotal = itemTotal.add(amount);
                   total = total.add(amount);
                   if (amount.compareTo(BigDecimal.ZERO) > 0) {
                       lineCount++;
                   }

                   if (LOGGER.isDebugEnabled()) {
                       LOGGER.debugf(
                               "[InvoiceCalc] Contract=%s, Item=%s, Event=IN (%s), InvoiceDate=%s → Days=%d, Rate=%.2f %s/%s → Total=%.2f %s",
                               contract.getId(), item.getId(), inDate, effectiveDate, days, rate.getAmount(),
                               rate.getCurrency(), rate.getUnitOfMeasurement(), amount.doubleValue(), rate.getCurrency());
                   }
               }

               if (LOGGER.isDebugEnabled() && itemTotal.compareTo(BigDecimal.ZERO) > 0) {
                   LOGGER.debugf("[InvoiceCalc] Item %s subtotal=%.2f", item.getId(), itemTotal.doubleValue());
               }
           }

           if (lineCount == 0) {
               LOGGER.debugf("[Invoice] BOL=%s — No eligible items/events found for invoicing (skipped)", billOfLadingId);
               return BigDecimal.ZERO;
           }

           if (LOGGER.isDebugEnabled()) {
               LOGGER.debugf("[InvoiceCalc] BillOfLading %s → Total=%.2f", billOfLadingId, total.doubleValue());
           }

           Invoice invoice = new Invoice();
           invoice.createdDate = effectiveDate;
           invoice.amount = total.doubleValue();
           invoice.billOfLadingId = billOfLadingId;
           invoice.itemIds = items.stream().map(i -> i.getId()).collect(Collectors.toList());

           saveInvoice(invoice);

           for (Item item : items) {
               item.setRelatedInvoice(invoice.id);
               updateItem(item);
           }

           return total;
       }

        protected void saveInvoice(Invoice invoice) {
            invoice.persist();
        }

        protected void updateItem(Item item) {
            item.update();
        }

        /**
         * Retrieve every invoice stored in the system.
         *
         * @return list containing all invoices
         */
        @Override
        public List<Invoice> getInvoices() {
                return Invoice.listAll();
        }

        @Override
        public List<Document> getInvoicesByVessel(String vesselName, LocalDate startDate, LocalDate endDate) {
                MongoCollection<Document> collection = Invoice.mongoCollection().withDocumentClass(Document.class);

                List<Bson> pipeline = new ArrayList<>();

                pipeline.add(Aggregates.match(Filters.and(
                                Filters.gte("createdDate", startDate),
                                Filters.lte("createdDate", endDate))));

                pipeline.add(Aggregates.lookup("VESSEL_VISIT", "facility", "facility", "shipping"));
                pipeline.add(Aggregates.unwind("$shipping"));
                pipeline.add(Aggregates.match(Filters.eq("shipping.vesselName", vesselName)));

                return collection.aggregate(pipeline).into(new ArrayList<>());
        }

        @Override
        public Document queryInvoices(int page, int pageSize, Bson sort,
                        List<String> statuses, String customerKey, String facilityKey,
                        String draftNumber, String finalNumber,
                        Date createdDateFrom, Date createdDateTo) {

                MongoCollection<Document> collection = Invoice.mongoCollection().withDocumentClass(Document.class);

                List<Bson> matchFilters = new ArrayList<>();
                if (statuses != null && !statuses.isEmpty()) {
                        matchFilters.add(Filters.in("status", statuses));
                }
                if (customerKey != null && !customerKey.isEmpty()) {
                        matchFilters.add(Filters.regex("customerKey", Pattern.compile("^" + Pattern.quote(customerKey))));
                }
                if (facilityKey != null && !facilityKey.isEmpty()) {
                        matchFilters.add(Filters.regex("facilityKey", Pattern.compile("^" + Pattern.quote(facilityKey))));
                }
                if (draftNumber != null && !draftNumber.isEmpty()) {
                        matchFilters.add(Filters.regex("draftNumber", Pattern.compile("^" + Pattern.quote(draftNumber))));
                }
                if (finalNumber != null && !finalNumber.isEmpty()) {
                        matchFilters.add(Filters.regex("finalNumber", Pattern.compile("^" + Pattern.quote(finalNumber))));
                }
                if (createdDateFrom != null || createdDateTo != null) {
                        List<Bson> dateFilters = new ArrayList<>();
                        if (createdDateFrom != null) {
                                dateFilters.add(Filters.gte("createdDate", createdDateFrom));
                        }
                        if (createdDateTo != null) {
                                dateFilters.add(Filters.lte("createdDate", createdDateTo));
                        }
                        matchFilters.add(Filters.and(dateFilters));
                }

                List<Bson> pipeline = new ArrayList<>();
                if (!matchFilters.isEmpty()) {
                        pipeline.add(Aggregates.match(Filters.and(matchFilters)));
                }

                List<Bson> itemsPipeline = new ArrayList<>();
                itemsPipeline.add(Aggregates.sort(sort));
                itemsPipeline.add(Aggregates.skip((page - 1) * pageSize));
                itemsPipeline.add(Aggregates.limit(pageSize));
               itemsPipeline.add(Aggregates.project(Projections.fields(
                               Projections.include("_id", "draftNumber", "finalNumber", "status", "customerName", "facility", "createdDate"),
                               Projections.computed("id", "$_id"),
                               Projections.computed("TotalAmount", "$amount"))));

                List<Bson> metaPipeline = new ArrayList<>();
                metaPipeline.add(Aggregates.group(null,
                                Accumulators.sum("totalCount", 1),
                                Accumulators.sum("totalAmount", new Document("$ifNull", List.of("$amount", 0)))));
                metaPipeline.add(Aggregates.project(Projections.fields(
                                Projections.excludeId(),
                                Projections.include("totalCount", "totalAmount"))));

                pipeline.add(Aggregates.facet(new Facet("items", itemsPipeline), new Facet("meta", metaPipeline)));

                Document result = collection.aggregate(pipeline).first();
                if (result == null) {
                        result = new Document("items", List.of()).append("meta", List.of());
                }
                return result;
        }

        @Override
        public void finalizeInvoice(String invoiceId) throws Exception {
                Invoice draftInvoice = Invoice.findById(invoiceId);

                if (draftInvoice == null) {
                        throw new Exception("Invoice not found");
                }

        var finalResult = invoiceLinePipeline.buildSnapshotForFinal(draftInvoice);
        if (!finalResult.missingItemIds().isEmpty()) {
                LOGGER.warnf("Skipped items without data: %s", finalResult.missingItemIds());
        }
        List<InvoiceLineSnap> snaps = finalResult.lines();
        if (snaps.isEmpty()) {
                throw new Exception("Cannot finalize: no eligible items/lines.");
        }

        String currency = snaps.get(0).currency != null ? snaps.get(0).currency : "EUR";

        draftInvoice.lines = snaps;
        draftInvoice.currency = currency;
        draftInvoice.subtotalAmount = finalResult.subtotal();
        draftInvoice.inclusiveTaxTotal = finalResult.inclusiveTaxTotal();
        draftInvoice.exclusiveTaxTotal = finalResult.exclusiveTaxTotal();
        draftInvoice.totalTaxAmount = finalResult.totalTax();
        draftInvoice.grandTotalAmount = finalResult.grandTotal();
        draftInvoice.taxBreakdown = finalResult.taxBreakdown();
        draftInvoice.taxCalculationIds = finalResult.taxCalculationIds();
        draftInvoice.amount = finalResult.grandTotal().doubleValue();
                draftInvoice.finalNumber = invoiceNumberService.generateFinalNumber();
                draftInvoice.status = "FINAL";

                draftInvoice.update();

                // Link PENDING charge records to this invoice and mark them INVOICED
                try {
                    if (draftInvoice.itemIds != null) {
                        for (String itemId : draftInvoice.itemIds) {
                            List<ChargeRecord> pending = ChargeRecord.findByItem(itemId).stream()
                                .filter(r -> "PENDING".equals(r.status) && r.invoiceId == null)
                                .toList();
                            for (ChargeRecord rec : pending) {
                                rec.invoiceId = draftInvoice.id;
                                rec.status = "INVOICED";
                                rec.update();
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warnf("Failed to update charge records for invoice %s: %s", draftInvoice.id, e.getMessage());
                }

                try {
                    domainEvents.publish(new InvoiceFinalized(
                            draftInvoice.id,
                            draftInvoice.finalNumber,
                            null,
                            draftInvoice.customerName,
                            draftInvoice.grandTotalAmount,
                            draftInvoice.currency,
                            java.time.Instant.now()));
                } catch (Exception e) {
                    LOGGER.warnf("Failed to publish InvoiceFinalized event for invoice %s: %s",
                            draftInvoice.id, e.getMessage());
                }
        }


    // Add this comprehensive debug method to InvoiceDaoImpl

    public void debugStatusComparison() {
        LOGGER.infof("[STATUS DEBUG] Starting status comparison test...");

        // Test 1: Query with enum
        List<Contract> enumResults = contractDao.findActiveContracts();
        LOGGER.infof("[STATUS DEBUG] Enum query (Status.ACTIVE) returned: %d contracts", enumResults.size());

        // Test 2: Query with string directly
        List<Contract> stringResults = Contract.list("status", "ACTIVE");
        LOGGER.infof("[STATUS DEBUG] String query ('ACTIVE') returned: %d contracts", stringResults.size());

        // Test 3: Get all contracts and check their status values
        List<Contract> allContracts = Contract.listAll();
        LOGGER.infof("[STATUS DEBUG] Total contracts in database: %d", allContracts.size());

        for (Contract contract : allContracts) {
            LOGGER.infof("[STATUS DEBUG] Contract %s: name=%s, status=%s (type: %s)",
                    contract.getId(),
                    contract.name,
                    contract.status,
                    contract.status != null ? contract.status.getClass().getSimpleName() : "NULL");

            // Check if this is the BL Volume contract
            if ("abfb729b-8b8d-4405-97f7-7726a68b3a91".equals(contract.getId())) {
                LOGGER.infof("[STATUS DEBUG] *** BL VOLUME CONTRACT STATUS DETAILS ***");
                LOGGER.infof("[STATUS DEBUG] Status value: '%s'", contract.status);
                LOGGER.infof("[STATUS DEBUG] Status type: %s", contract.status.getClass().getName());

                // Test enum comparison
                if (contract.status instanceof Status) {
                    Status statusEnum = (Status) contract.status;
                    boolean enumMatch = Status.ACTIVE.equals(statusEnum);
                    LOGGER.infof("[STATUS DEBUG] Enum comparison result: %s", enumMatch);
                } else {
                    LOGGER.infof("[STATUS DEBUG] Status is NOT a Status enum, it's a %s",
                            contract.status.getClass().getName());
                }
            }
        }

        // Test 4: Status enum value inspection
        LOGGER.infof("[STATUS DEBUG] Status.ACTIVE enum value: %s", Status.ACTIVE);
        LOGGER.infof("[STATUS DEBUG] Status.ACTIVE toString(): %s", Status.ACTIVE.toString());
    }

    /**
     * Computes an idempotency key from sorted item IDs and customer name.
     * Identical inputs always produce the same key, preventing duplicate drafts.
     */
    private String computeIdempotencyKey(List<String> itemIds, String customerName) {
        try {
            List<String> sorted = new ArrayList<>(itemIds);
            Collections.sort(sorted);
            String raw = String.join(",", sorted) + "|" + (customerName != null ? customerName.trim().toLowerCase() : "");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @Override
    public List<Document> enrichWithPaymentSummary(List<Document> items) {
        if (items == null || items.isEmpty()) return items;

        // Collect invoice IDs as strings (Mongo _id is ObjectId; toString() yields the hex).
        List<String> ids = new ArrayList<>(items.size());
        for (Document d : items) {
            Object oid = d.get("_id");
            if (oid != null) ids.add(oid.toString());
        }
        if (ids.isEmpty()) return items;

        // Fetch every Payment that has an allocation matching one of these invoiceIds.
        var paymentsCol = fr.alb.billing.model.Payment.mongoCollection().withDocumentClass(Document.class);
        var cursor = paymentsCol.find(Filters.in("allocations.invoiceId", ids)).iterator();

        Map<String, BigDecimal> paidByInvoice = new HashMap<>();
        Map<String, Instant>    lastByInvoice = new HashMap<>();
        try {
            while (cursor.hasNext()) {
                Document p = cursor.next();
                Object pdRaw = p.get("paymentDate");
                Instant pd = (pdRaw instanceof java.util.Date)
                    ? ((java.util.Date) pdRaw).toInstant()
                    : null;

                Object allocs = p.get("allocations");
                if (!(allocs instanceof List<?>)) continue;
                for (Object a : (List<?>) allocs) {
                    if (!(a instanceof Document)) continue;
                    Document alloc = (Document) a;
                    String invId = alloc.getString("invoiceId");
                    if (invId == null || !ids.contains(invId)) continue;

                    Object amtRaw = alloc.get("allocatedAmount");
                    BigDecimal amt = toBigDecimal(amtRaw);
                    if (amt != null) {
                        paidByInvoice.merge(invId, amt, BigDecimal::add);
                    }
                    if (pd != null) {
                        lastByInvoice.merge(invId, pd, (cur, neu) -> neu.isAfter(cur) ? neu : cur);
                    }
                }
            }
        } finally {
            cursor.close();
        }

        // Mutate items.
        for (Document d : items) {
            Object oid = d.get("_id");
            String invId = oid == null ? null : oid.toString();
            BigDecimal paid = paidByInvoice.getOrDefault(invId, BigDecimal.ZERO);
            d.put("paidAmount", paid);
            Instant last = lastByInvoice.get(invId);
            d.put("lastPaymentDate", last == null ? null : last.toString()); // ISO-8601
        }
        return items;
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return new BigDecimal(o.toString());
        if (o instanceof String) {
            try { return new BigDecimal((String) o); }
            catch (NumberFormatException e) { return null; }
        }
        if (o instanceof org.bson.types.Decimal128) return ((org.bson.types.Decimal128) o).bigDecimalValue();
        return null;
    }

}
