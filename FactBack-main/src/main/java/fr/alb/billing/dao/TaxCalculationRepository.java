package fr.alb.billing.dao;

import java.time.Instant;
import java.util.List;

import org.bson.Document;
import org.jboss.logging.Logger;

import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.Indexes;

import fr.alb.billing.model.TaxCalculation;
import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import io.quarkus.mongodb.panache.PanacheQuery;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TaxCalculationRepository implements PanacheMongoRepositoryBase<TaxCalculation, String> {

        private static final Logger LOG = Logger.getLogger(TaxCalculationRepository.class);

        @PostConstruct
        void ensureIndexes() {
                try {
                        MongoCollection<Document> collection = TaxCalculation.mongoCollection().withDocumentClass(Document.class);
                        collection.createIndexes(List.of(
                                        new IndexModel(Indexes.ascending("contractId")),
                                        new IndexModel(Indexes.ascending("contractRateId")),
                                        new IndexModel(Indexes.ascending("invoiceId")),
                                        new IndexModel(Indexes.ascending("calculationDate"))
                        ));
                } catch (MongoTimeoutException e) {
                        LOG.warn("Timed out while creating indexes for TaxCalculation collection", e);
                } catch (MongoException e) {
                        LOG.error("Failed to create indexes for TaxCalculation collection", e);
                        throw new IllegalStateException("Unable to create indexes for TaxCalculation collection", e);
                }
        }

        public List<TaxCalculation> findByContractId(String contractId, int limit, int offset) {
                if (contractId == null) {
                        return List.of();
                }
                return applyRange(find("contractId", contractId), limit, offset);
        }

        public List<TaxCalculation> findByRateId(String rateId, int limit, int offset) {
                if (rateId == null) {
                        return List.of();
                }
                return applyRange(find("contractRateId", rateId), limit, offset);
        }

        public List<TaxCalculation> findByInvoiceId(String invoiceId, int limit, int offset) {
                if (invoiceId == null) {
                        return List.of();
                }
                return applyRange(find("invoiceId", invoiceId), limit, offset);
        }

        public List<TaxCalculation> findByCalculationDateRange(Instant from, Instant to, int limit, int offset) {
                if (from == null && to == null) {
                        return applyRange(findAll(), limit, offset);
                }
                if (from != null && to != null) {
                        return applyRange(find("calculationDate between ?1 and ?2", from, to), limit, offset);
                }
                if (from != null) {
                        return applyRange(find("calculationDate >= ?1", from), limit, offset);
                }
                return applyRange(find("calculationDate <= ?1", to), limit, offset);
        }

        private List<TaxCalculation> applyRange(PanacheQuery<TaxCalculation> query, int limit, int offset) {
                int effectiveLimit = limit > 0 ? limit : 50;
                int effectiveOffset = Math.max(offset, 0);
                int end = effectiveOffset + effectiveLimit - 1;
                return query.range(effectiveOffset, end).list();
        }
}
