package fr.alb.billing.model;

import fr.alb.model.EntityBase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fr.alb.type.TaxType;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "TAX_CALCULATION")
public class TaxCalculation extends EntityBase {

        private static final long serialVersionUID = 1L;

        private String contractId;
        private String contractRateId;
        private String invoiceId;
        private Instant calculationDate;
        private BigDecimal baseAmount;
        private boolean inclusive;
        private List<AppliedTax> appliedTaxes = new ArrayList<>();
        private BigDecimal finalAmount;
        private CalculationMetadata metadata;

        public TaxCalculation() {
                super();
        }

        public String getContractId() {
                return contractId;
        }

        public void setContractId(String contractId) {
                this.contractId = contractId;
        }

        public String getContractRateId() {
                return contractRateId;
        }

        public void setContractRateId(String contractRateId) {
                this.contractRateId = contractRateId;
        }

        public String getInvoiceId() {
                return invoiceId;
        }

        public void setInvoiceId(String invoiceId) {
                this.invoiceId = invoiceId;
        }

        public Instant getCalculationDate() {
                return calculationDate;
        }

        public void setCalculationDate(Instant calculationDate) {
                this.calculationDate = calculationDate;
        }

        public BigDecimal getBaseAmount() {
                return baseAmount;
        }

        public void setBaseAmount(BigDecimal baseAmount) {
                this.baseAmount = baseAmount;
        }

        public boolean isInclusive() {
                return inclusive;
        }

        public void setInclusive(boolean inclusive) {
                this.inclusive = inclusive;
        }

        public List<AppliedTax> getAppliedTaxes() {
                return appliedTaxes;
        }

        public void setAppliedTaxes(List<AppliedTax> appliedTaxes) {
                this.appliedTaxes = appliedTaxes;
        }

        public BigDecimal getFinalAmount() {
                return finalAmount;
        }

        public void setFinalAmount(BigDecimal finalAmount) {
                this.finalAmount = finalAmount;
        }

        public CalculationMetadata getMetadata() {
                return metadata;
        }

        public void setMetadata(CalculationMetadata metadata) {
                this.metadata = metadata;
        }

        public static class AppliedTax {
                private String taxId;
                private String code;
                private String name;
                private TaxType type;
                private BigDecimal rate;
                private BigDecimal baseAmount;
                private BigDecimal taxAmount;

                public AppliedTax() {}

                public AppliedTax(String taxId, String code, String name, TaxType type,
                                BigDecimal rate, BigDecimal baseAmount, BigDecimal taxAmount) {
                        this.taxId = taxId;
                        this.code = code;
                        this.name = name;
                        this.type = type;
                        this.rate = rate;
                        this.baseAmount = baseAmount;
                        this.taxAmount = taxAmount;
                }

                public String getTaxId() {
                        return taxId;
                }

                public void setTaxId(String taxId) {
                        this.taxId = taxId;
                }

                public String getCode() {
                        return code;
                }

                public void setCode(String code) {
                        this.code = code;
                }

                public String getName() {
                        return name;
                }

                public void setName(String name) {
                        this.name = name;
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

                public BigDecimal getBaseAmount() {
                        return baseAmount;
                }

                public void setBaseAmount(BigDecimal baseAmount) {
                        this.baseAmount = baseAmount;
                }

                public BigDecimal getTaxAmount() {
                        return taxAmount;
                }

                public void setTaxAmount(BigDecimal taxAmount) {
                        this.taxAmount = taxAmount;
                }
        }

        public static class CalculationMetadata {
                private String triggeredBy;
                private String source;
                private String correlationId;
                private Map<String, Object> extra;

                public CalculationMetadata() {}

                public CalculationMetadata(String triggeredBy, String source, String correlationId,
                                Map<String, Object> extra) {
                        this.triggeredBy = triggeredBy;
                        this.source = source;
                        this.correlationId = correlationId;
                        this.extra = extra;
                }

                public String getTriggeredBy() {
                        return triggeredBy;
                }

                public void setTriggeredBy(String triggeredBy) {
                        this.triggeredBy = triggeredBy;
                }

                public String getSource() {
                        return source;
                }

                public void setSource(String source) {
                        this.source = source;
                }

                public String getCorrelationId() {
                        return correlationId;
                }

                public void setCorrelationId(String correlationId) {
                        this.correlationId = correlationId;
                }

                public Map<String, Object> getExtra() {
                        return extra;
                }

                public void setExtra(Map<String, Object> extra) {
                        this.extra = extra;
                }
        }
}
