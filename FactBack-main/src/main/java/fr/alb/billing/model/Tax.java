package fr.alb.billing.model;

import fr.alb.model.EntityBase;

import java.math.BigDecimal;
import java.time.Instant;

import fr.alb.type.TaxType;
import io.quarkus.mongodb.panache.common.MongoEntity;

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

        public boolean isActive() {
                return isActive;
        }

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
