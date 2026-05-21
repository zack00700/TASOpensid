package fr.alb.model;

import java.time.Instant;

import org.bson.Document;

import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "THIRD_PARTY_HISTORY")
public class HistoryEntry extends EntityBase {

    public String thirdPartyId;
    public Long version;
    public Document data;
    public Instant updatedAt;

    public HistoryEntry() {
        super();
    }
}
