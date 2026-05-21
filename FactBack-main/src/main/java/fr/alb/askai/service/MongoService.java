package fr.alb.askai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class MongoService {

    private static final Logger LOG = Logger.getLogger(MongoService.class);

    private static final Set<String> ALLOWED_COLLECTIONS = Set.of(
            "INVOICE",          // <-- as requested
            "INVOICES",         // (optional aliases in case LLM varies)
            "CUSTOMER",
            "CUSTOMERS",
            "PAYMENT",
            "PAYMENTS",
            "BillOfLading",
            "BillOfLadings"
    );

    @Inject MongoClient client;
    @Inject ObjectMapper mapper;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "app")
    String database;

    public List<Document> aggregate(String collection, List<JsonNode> pipeline) {
        MongoCollection<Document> col = getCollection(collection);
        List<Bson> bsonPipeline = toDocuments(pipeline);
        return col.aggregate(bsonPipeline)
                .allowDiskUse(true)
                .into(new ArrayList<>());
    }

    public List<Document> aggregateDocuments(String collection, List<Document> pipeline) {
        MongoCollection<Document> col = getCollection(collection);
        List<Document> stages = pipeline == null ? List.of() : pipeline;
        return col.aggregate(stages)
                .allowDiskUse(true)
                .into(new ArrayList<>());
    }

    private List<Bson> toDocuments(List<JsonNode> pipeline) {
        if (pipeline == null || pipeline.isEmpty()) return List.of();
        return pipeline.stream().map(this::toDocument).collect(Collectors.toList());
    }

    private Document toDocument(JsonNode node) {
        try {
            // Jackson -> JSON string with $ operators preserved, then parse to Document.
            String json = mapper.writeValueAsString(node);
            return Document.parse(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pipeline stage: " + node, e);
        }
    }

    private MongoCollection<Document> getCollection(String collection) {
        if (collection == null) throw new IllegalArgumentException("Collection is required");
        String coll = collection.trim();

        if (!ALLOWED_COLLECTIONS.contains(coll)) {
            LOG.errorf("Collection not allowed: %s", coll);
            throw new IllegalArgumentException("Collection not allowed: " + coll);
        }

        MongoDatabase db = client.getDatabase(database);
        return db.getCollection(coll);
    }
}
