package fr.alb.sequence.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TicketSequenceService {

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "tos3d")
    String database;

    /**
     * Atomically increment and return next ticket number as "FR-YYYY-NNNN".
     */
    public String nextTicketNumber() {
        int year = java.time.LocalDate.now().getYear();
        String seqKey = "FR-" + year;

        Document result = mongoClient.getDatabase(database)
            .getCollection("SEQUENCES")
            .findOneAndUpdate(
                new Document("_id", seqKey),
                Updates.inc("seq", 1),
                new FindOneAndUpdateOptions()
                    .returnDocument(ReturnDocument.AFTER)
                    .upsert(true)
            );

        long seq = result != null ? ((Number) result.get("seq")).longValue() : 1L;
        return String.format("FR-%d-%04d", year, seq);
    }
}
