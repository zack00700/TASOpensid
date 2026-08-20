package fr.alb.billing.model;

import fr.alb.model.EntityBase;

import java.math.BigDecimal;
import java.time.Instant;

import fr.alb.type.TaxType;
import io.quarkus.mongodb.panache.common.MongoEntity;
import com.fasterxml.jackson.annotation.JsonProperty;

@MongoEntity(collection = "TAX")
public class Tax extends EntityBase {

        private static final long serialVersionUID = 1L;

        private String name;
        private String code;
        private TaxType type;
        private BigDecimal rate;
        private Instant validFrom;
        private Instant validTo;
        private boolean isActive = true;

        public Tax() {
                super();
        }

        public String getName() {
                return name;
        }

        public void setName(String name) {
                this.name = name;
        }

        public String getCode() {
                return code;
        }

        public void setCode(String code) {
                this.code = code;
        }

        public TaxType getType() {
                return type;
        }

        public void setType(TaxType type) {
                this.type = type;
        }

        public BigDecimal getRate() {
                return rate;
        }

        public void setRate(BigDecimal rate) {
                this.rate = rate;
        }

        public Instant getValidFrom() {
                return validFrom;
        }

        public void setValidFrom(Instant validFrom) {
                this.validFrom = validFrom;
        }

        public Instant getValidTo() {
                return validTo;
        }

        public void setValidTo(Instant validTo) {
                this.validTo = validTo;
        }

        // Jackson would otherwise serialise this as "active" (bean-property
        // naming strips the leading "is"). The frontend reads and sends
        // "isActive" to match the Mongo/Java field name, so pin the JSON
        // key explicitly to keep the two ends aligned.
        @JsonProperty("isActive")
        public boolean isActive() {
                return isActive;
        }

        @JsonProperty("isActive")
        public void setActive(boolean active) {
                isActive = active;
        }

        public boolean isInForce(Instant at) {
                if (!isActive) {
                        return false;
                }
                Instant reference = at != null ? at : Instant.now();
                if (validFrom != null && reference.isBefore(validFrom)) {
                        return false;
                }
                if (validTo != null && reference.isAfter(validTo)) {
                        return false;
                }
                return true;
        }
}
