package fr.alb.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;
import org.bson.BsonType;

public abstract class EntityBase extends PanacheMongoEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	/** Mongo id property - stored as string in MongoDB */
        @BsonId
        @BsonRepresentation(BsonType.STRING)
        public String id;

        /** Soft delete flag. */
        public boolean deleted;

        /** Timestamp when the entity was soft-deleted. */
        public Instant deletedAt;

        /** Optimistic locking/version counter. */
        public Long version;

        /** Audit information. */
        public String createdBy;
        public Instant createdAt;
        public String updatedBy;
        public Instant updatedAt;

	public EntityBase() {
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString();
		}
	}

	@JsonProperty("id")
        public String getId() { return id; }

        public void setId(String id) { this.id = id; }

        public boolean isDeleted() {
                return deleted;
        }

        public void setDeleted(boolean deleted) {
                this.deleted = deleted;
                if (deleted) {
                        this.deletedAt = Instant.now();
                }
        }

        public Instant getDeletedAt() {
                return deletedAt;
        }

        public void setDeletedAt(Instant deletedAt) {
                this.deletedAt = deletedAt;
        }

        public Long getVersion() {
                return version;
        }

        public void setVersion(Long version) {
                this.version = version;
        }

        public String getCreatedBy() {
                return createdBy;
        }

        public void setCreatedBy(String createdBy) {
                this.createdBy = createdBy;
        }

        public Instant getCreatedAt() {
                return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
                this.createdAt = createdAt;
        }

        public String getUpdatedBy() {
                return updatedBy;
        }

        public void setUpdatedBy(String updatedBy) {
                this.updatedBy = updatedBy;
        }

        public Instant getUpdatedAt() {
                return updatedAt;
        }

        public void setUpdatedAt(Instant updatedAt) {
                this.updatedAt = updatedAt;
        }
}