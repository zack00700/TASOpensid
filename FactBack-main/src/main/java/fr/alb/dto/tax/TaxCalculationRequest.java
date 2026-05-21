package fr.alb.dto.tax;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class TaxCalculationRequest {

        @NotNull
        @DecimalMin(value = "0", inclusive = true)
        private BigDecimal baseAmount;
        private List<String> taxIds;
        private boolean inclusive;
        private Instant calculationDate;
        private String contractId;
        private String contractRateId;
        private String invoiceId;
        private String currency;
        private String triggeredBy;
        private String source;
        private String correlationId;
        private Map<String, Object> metadata;

        public BigDecimal getBaseAmount() {
                return baseAmount;
        }

        public void setBaseAmount(BigDecimal baseAmount) {
                this.baseAmount = baseAmount;
        }

        public List<String> getTaxIds() {
                return taxIds;
        }

        public void setTaxIds(List<String> taxIds) {
                this.taxIds = taxIds;
        }

        public boolean isInclusive() {
                return inclusive;
        }

        public void setInclusive(boolean inclusive) {
                this.inclusive = inclusive;
        }

        public Instant getCalculationDate() {
                return calculationDate;
        }

        public void setCalculationDate(Instant calculationDate) {
                this.calculationDate = calculationDate;
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

        public String getCurrency() {
                return currency;
        }

        public void setCurrency(String currency) {
                this.currency = currency;
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

        public Map<String, Object> getMetadata() {
                return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
                this.metadata = metadata;
        }
}
