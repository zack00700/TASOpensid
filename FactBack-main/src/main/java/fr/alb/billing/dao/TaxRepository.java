package fr.alb.billing.dao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.jboss.logging.Logger;

import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import fr.alb.billing.model.Tax;
import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TaxRepository implements PanacheMongoRepositoryBase<Tax, String> {

        private static final Logger LOG = Logger.getLogger(TaxRepository.class);

        @PostConstruct
        void ensureIndexes() {
                try {
                        MongoCollection<Document> collection = Tax.mongoCollection().withDocumentClass(Document.class);
                        collection.createIndexes(List.of(
                                        new IndexModel(Indexes.ascending("code"), new IndexOptions().unique(true)),
                                        new IndexModel(Indexes.compoundIndex(
                                                        Indexes.ascending("isActive"),
                                                        Indexes.ascending("validFrom"),
                                                        Indexes.ascending("validTo")))
                        ));
                } catch (MongoTimeoutException e) {
                        LOG.warn("Timed out while creating indexes for Tax collection", e);
                } catch (MongoException e) {
                        LOG.error("Failed to create indexes for Tax collection", e);
                        throw new IllegalStateException("Unable to create indexes for Tax collection", e);
                }
        }

        public Optional<Tax> findByCode(String code) {
                if (code == null) {
                        return Optional.empty();
                }
                return find("code", code).firstResultOptional();
        }

        public List<Tax> findActiveTaxes(Instant at) {
                Instant ref = at != null ? at : Instant.now();
                return find("isActive = ?1 and (validFrom = null or validFrom <= ?2) and (validTo = null or validTo >= ?2)",
                                true, ref).list();
        }

        public void softDelete(String id) {
                Tax tax = findById(id);
                if (tax != null) {
                        tax.setActive(false);
                        tax.setDeleted(true);
                        tax.update();
                }
        }

        public List<Tax> findByIds(List<String> ids, Instant at) {
                if (ids == null || ids.isEmpty()) {
                        return List.of();
                }
                Instant ref = at != null ? at : Instant.now();
                return find("_id in ?1 and isActive = true", ids).stream()
                                .filter(t -> t != null && t.isInForce(ref))
                                .toList();
        }

        public List<Tax> findAllActive() {
                return find("isActive", true).list();
        }
}
