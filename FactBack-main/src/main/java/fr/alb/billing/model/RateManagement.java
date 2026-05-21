package fr.alb.billing.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import fr.alb.type.RateType;
import fr.alb.type.TaxType;

public class RateManagement {

        private String rateId;
        private double startQuantity;
        private double endQuantity;
        private String unitOfMeasurement;
        private double amount;
        private double flatCost;
        private String currency;
        private Date startDate;
        private Date endDate;
        private boolean isDefaultRate;
        private int priority;
        private List<RateTax> taxes;
        private TaxCalculationSummary lastTaxSummary;

        // N4 extensions
        /** Pricing strategy for this rate row (SIMPLE, TIERED, BANDED, VOLUME, CUSTOM). */
        private RateType rateType;
        /** General Ledger code for accounting integration. */
        private String glCode;
        /** Minimum invoiceable amount for this rate (floor). */
        private BigDecimal minAmount;
        /** Maximum invoiceable amount for this rate (cap). */
        private BigDecimal maxAmount;
        /** Divisor applied to the raw quantity before rate calculation (e.g. 1000 for per-tonne from kg). */
        private BigDecimal quantityDivisor;
        /** If set, this rate only applies to items with this category (Import/Export/Transship). Null = applies to all. */
        private String applicableCategory;
        /** If set, this rate only applies to items with this freight kind (FCL/LCL/Empty/...). Null = applies to all. */
        private String applicableFreightKind;

        public RateManagement() {}

        public RateManagement(double startQuantity, double endQuantity, String unitOfMeasurement, double amount,
                        String currency, Date startDate, Date endDate, boolean isDefaultRate, int priority) {

                super();

                this.startQuantity = startQuantity;
                this.endQuantity = endQuantity;
                this.unitOfMeasurement = unitOfMeasurement;
                this.amount = amount;
                this.currency = currency;
                this.startDate = startDate;
                this.endDate = endDate;
                this.isDefaultRate = isDefaultRate;
                this.priority = priority;
        }

        public String getRateId() {
                return rateId;
        }

        public void setRateId(String rateId) {
                this.rateId = rateId;
        }

        public String ensureRateId() {
                if (rateId == null || rateId.isBlank()) {
                        rateId = UUID.randomUUID().toString();
                }
                return rateId;
        }

        public double getStartQuantity() {
                return startQuantity;
        }


        public void setStartQuantity(double startQuantity) {

                /*if (startQuantity <= 0) {
                        throw new IllegalArgumentException("Start quantity must be more than 0");
                }*/
                this.startQuantity = startQuantity;
        }


        public double getEndQuantity() {
                return endQuantity;
        }


        public void setEndQuantity(double endQuantity) {

                /*if (endQuantity <= 0 || endQuantity < startQuantity) {
                        throw new IllegalArgumentException("End quantity must be more than 0 and greater than start quantity");
                }*/

                this.endQuantity = endQuantity;
        }


        public String getUnitOfMeasurement() {
                return unitOfMeasurement;
        }


        public void setUnitOfMeasurement(String unitOfMeasurement) {
                this.unitOfMeasurement = unitOfMeasurement;
        }


        public double getAmount() {
                return amount;
        }

        public void setAmount(double amount) {
                this.amount = amount;
        }

        public double getFlatCost() {
                return flatCost;
        }

        public void setFlatCost(double flatCost) {
                this.flatCost = flatCost;
        }

        public String getCurrency() {
                return currency;
        }


        public void setCurrency(String currency) {
                this.currency = currency;
        }


        public Date getStartDate() {
                return startDate;
        }


        public void setStartDate(Date startDate) {
                this.startDate = startDate;
        }


        public Date getEndDate() {
                return endDate;
        }


        public void setEndDate(Date endDate) {
                this.endDate = endDate;
        }


        public boolean isDefaultRate() {
                return isDefaultRate;
        }


        public void setDefaultRate(boolean isDefaultRate) {
                this.isDefaultRate = isDefaultRate;
        }


        public int getPriority() {
                return priority;
        }


        public void setPriority(int priority) {
                this.priority = priority;
        }

        public List<RateTax> getTaxes() {
                return taxes;
        }

        public void setTaxes(List<RateTax> taxes) {
                this.taxes = taxes;
        }

        public TaxCalculationSummary getLastTaxSummary() {
                return lastTaxSummary;
        }

        public void setLastTaxSummary(TaxCalculationSummary lastTaxSummary) {
                this.lastTaxSummary = lastTaxSummary;
        }

        public RateType getRateType() { return rateType; }
        public void setRateType(RateType rateType) { this.rateType = rateType; }

        public String getGlCode() { return glCode; }
        public void setGlCode(String glCode) { this.glCode = glCode; }

        public BigDecimal getMinAmount() { return minAmount; }
        public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }

        public BigDecimal getMaxAmount() { return maxAmount; }
        public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }

        public BigDecimal getQuantityDivisor() { return quantityDivisor; }
        public void setQuantityDivisor(BigDecimal quantityDivisor) { this.quantityDivisor = quantityDivisor; }

        public String getApplicableCategory() { return applicableCategory; }
        public void setApplicableCategory(String applicableCategory) { this.applicableCategory = applicableCategory; }

        public String getApplicableFreightKind() { return applicableFreightKind; }
        public void setApplicableFreightKind(String applicableFreightKind) { this.applicableFreightKind = applicableFreightKind; }

        public static class RateTax {
                private String taxId;
                private boolean inclusive;

                public RateTax() {}

                public RateTax(String taxId, boolean inclusive) {
                        this.taxId = taxId;
                        this.inclusive = inclusive;
                }

                public String getTaxId() {
                        return taxId;
                }

                public void setTaxId(String taxId) {
                        this.taxId = taxId;
                }

                public boolean isInclusive() {
                        return inclusive;
                }

                public void setInclusive(boolean inclusive) {
                        this.inclusive = inclusive;
                }
        }

        public static class TaxBreakdownItem {
                private String taxId;
                private String code;
                private String name;
                private BigDecimal rate;
                private TaxType type;
                private BigDecimal baseAmount;
                private BigDecimal taxAmount;

                public TaxBreakdownItem() {}

                public TaxBreakdownItem(String taxId, String code, String name, BigDecimal rate,
                                TaxType type, BigDecimal baseAmount, BigDecimal taxAmount) {
                        this.taxId = taxId;
                        this.code = code;
                        this.name = name;
                        this.rate = rate;
                        this.type = type;
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

                public BigDecimal getRate() {
                        return rate;
                }

                public void setRate(BigDecimal rate) {
                        this.rate = rate;
                }

                public TaxType getType() {
                        return type;
                }

                public void setType(TaxType type) {
                        this.type = type;
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

        public static class TaxCalculationSummary {
                private BigDecimal baseAmount;
                private BigDecimal totalTax;
                private BigDecimal finalAmount;
                private List<TaxBreakdownItem> breakdown = new ArrayList<>();
                private Instant calculatedAt;
                private String currency;

                public TaxCalculationSummary() {}

                public TaxCalculationSummary(BigDecimal baseAmount, BigDecimal totalTax, BigDecimal finalAmount,
                                List<TaxBreakdownItem> breakdown, Instant calculatedAt, String currency) {
                        this.baseAmount = baseAmount;
                        this.totalTax = totalTax;
                        this.finalAmount = finalAmount;
                        if (breakdown != null) {
                                this.breakdown = new ArrayList<>(breakdown);
                        }
                        this.calculatedAt = calculatedAt;
                        this.currency = currency;
                }

                public BigDecimal getBaseAmount() {
                        return baseAmount;
                }

                public void setBaseAmount(BigDecimal baseAmount) {
                        this.baseAmount = baseAmount;
                }

                public BigDecimal getTotalTax() {
                        return totalTax;
                }

                public void setTotalTax(BigDecimal totalTax) {
                        this.totalTax = totalTax;
                }

                public BigDecimal getFinalAmount() {
                        return finalAmount;
                }

                public void setFinalAmount(BigDecimal finalAmount) {
                        this.finalAmount = finalAmount;
                }

                public List<TaxBreakdownItem> getBreakdown() {
                        return breakdown;
                }

                public void setBreakdown(List<TaxBreakdownItem> breakdown) {
                        this.breakdown = breakdown;
                }

                public Instant getCalculatedAt() {
                        return calculatedAt;
                }

                public void setCalculatedAt(Instant calculatedAt) {
                        this.calculatedAt = calculatedAt;
                }

                public String getCurrency() {
                        return currency;
                }

                public void setCurrency(String currency) {
                        this.currency = currency;
                }
        }
}
