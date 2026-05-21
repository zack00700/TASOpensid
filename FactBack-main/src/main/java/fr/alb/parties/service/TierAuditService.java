package fr.alb.parties.service;

import java.time.Instant;

import fr.alb.parties.model.TierAudit;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.bson.Document;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import com.mongodb.client.model.changestream.UpdateDescription;
import com.mongodb.client.model.changestream.FullDocument;

@ApplicationScoped
public class TierAuditService {

    @Inject
    MongoClient mongoClient;

    @PostConstruct
    void init() {
        MongoDatabase db = mongoClient.getDatabase("tos3d");
        MongoCollection<Document> col = db.getCollection("THIRDPARTY");
        MongoCollection<TierAudit> audit = db.getCollection("TIER_AUDIT", TierAudit.class);
        col.watch().fullDocument(FullDocument.UPDATE_LOOKUP).forEach((ChangeStreamDocument<Document> evt) -> {
            if(evt.getOperationType() == OperationType.UPDATE || evt.getOperationType() == OperationType.REPLACE) {
                TierAudit ta = new TierAudit();
                ta.tierId = evt.getDocumentKey().getString("_id").getValue();
                ta.eventType = "update";
                ta.createdAt = Instant.now();
                UpdateDescription desc = evt.getUpdateDescription();
                Document diff = new Document();
                if(desc != null) {
                    diff.put("updatedFields", desc.getUpdatedFields());
                    diff.put("removedFields", desc.getRemovedFields());
                }
                ta.diff = diff;
                Document full = evt.getFullDocument();
                if(full != null && full.containsKey("version")) {
                    ta.version = full.getLong("version");
                }
                audit.insertOne(ta);
            }
        });
    }
}
