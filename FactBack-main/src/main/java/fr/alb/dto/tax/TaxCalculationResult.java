package fr.alb.dto.tax;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import fr.alb.billing.model.RateManagement;

public class TaxCalculationResult {

        private String calculationId;
        private BigDecimal baseAmount;
        private BigDecimal netAmount;
        private BigDecimal totalTaxAmount;
        private BigDecimal inclusiveTaxAmount;
        private BigDecimal exclusiveTaxAmount;
        private BigDecimal finalAmount;
        private String currency;
        private List<RateManagement.TaxBreakdownItem> taxBreakdown = new ArrayList<>();
        private Instant calculationDate;
        private References references;

        public String getCalculationId() {
                return calculationId;
        }

        public void setCalculationId(String calculationId) {
                this.calculationId = calculationId;
        }

        public BigDecimal getBaseAmount() {
                return baseAmount;
        }

        public void setBaseAmount(BigDecimal baseAmount) {
                this.baseAmount = baseAmount;
        }

        public BigDecimal getNetAmount() {
                return netAmount;
        }

        public void setNetAmount(BigDecimal netAmount) {
                this.netAmount = netAmount;
        }

        public BigDecimal getTotalTaxAmount() {
                return totalTaxAmount;
        }

        public void setTotalTaxAmount(BigDecimal totalTaxAmount) {
                this.totalTaxAmount = totalTaxAmount;
        }

        public BigDecimal getInclusiveTaxAmount() {
                return inclusiveTaxAmount;
        }

        public void setInclusiveTaxAmount(BigDecimal inclusiveTaxAmount) {
                this.inclusiveTaxAmount = inclusiveTaxAmount;
        }

        public BigDecimal getExclusiveTaxAmount() {
                return exclusiveTaxAmount;
        }

        public void setExclusiveTaxAmount(BigDecimal exclusiveTaxAmount) {
                this.exclusiveTaxAmount = exclusiveTaxAmount;
        }

        public BigDecimal getFinalAmount() {
                return finalAmount;
        }

        public void setFinalAmount(BigDecimal finalAmount) {
                this.finalAmount = finalAmount;
        }

        public String getCurrency() {
                return currency;
        }

        public void setCurrency(String currency) {
                this.currency = currency;
        }

        public List<RateManagement.TaxBreakdownItem> getTaxBreakdown() {
                return taxBreakdown;
        }

        public void setTaxBreakdown(List<RateManagement.TaxBreakdownItem> taxBreakdown) {
                this.taxBreakdown = taxBreakdown;
        }

        public Instant getCalculationDate() {
                return calculationDate;
        }

        public void setCalculationDate(Instant calculationDate) {
                this.calculationDate = calculationDate;
        }

        public References getReferences() {
                return references;
        }

        public void setReferences(References references) {
                this.references = references;
        }

        public static class References {
                private String contractId;
                private String contractRateId;
                private String invoiceId;

                public References() {}

                public References(String contractId, String contractRateId, String invoiceId) {
                        this.contractId = contractId;
                        this.contractRateId = contractRateId;
                        this.invoiceId = invoiceId;
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
        }
}
