package fr.alb.parties.model;

import java.time.Instant;
import org.bson.Document;
import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "TIER_AUDIT")
public class TierAudit extends EntityBase {

    public String tierId;
    public Long version;
    public Instant createdAt;
    public Document diff;
    public String eventType;

    public TierAudit() {
        super();
    }
}
