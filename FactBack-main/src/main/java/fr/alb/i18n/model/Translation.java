package fr.alb.i18n.model;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonId;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@MongoEntity(collection = "i18n_translations")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Translation extends PanacheMongoEntityBase {

    @BsonId
    public String id; // "<locale>:<key>"

    public String locale;
    public String key;
    public String value;
    public Instant updatedAt;
    public String updatedBy;

    public Translation() {}

    public Translation(String locale, String key, String value, String updatedBy) {
        if (locale == null || locale.isBlank()) {
            throw new IllegalArgumentException("locale must not be null or blank");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be null or blank");
        }
        this.locale = locale;
        this.key = key;
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
        this.id = locale + ":" + key;
    }

    @Override
    public void persist() {
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
        super.persist();
    }

    @Override
    public void update() {
        this.updatedAt = Instant.now();
        super.update();
    }
}
