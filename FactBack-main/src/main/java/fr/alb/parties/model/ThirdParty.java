package fr.alb.parties.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import fr.alb.model.EntityBase;
import fr.alb.type.RequiredModules;
import fr.alb.type.Status;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection= "THIRDPARTY")
public class ThirdParty extends EntityBase {
	
	public static final long serialVersionUID = 1L;
	public String fullName;
	public String jobTitle;
	public String contactNumber;
	public String email;
	public String companyName;
	public String companyAddress;
	public String industryType;
	public String companyContactPerson;
	public String companyContactEmail;
	public String accessType;
        public Status status;
        public Long version;
        public Instant updatedAt;
        public List<RequiredModules> modulesRequired;
	public Date accessStartDate;
	public Date accessEndDate;
	public String identificationType;
	public String identificationNumber;
	public Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

        // === Customer/billing fields ===

        /** Unique customer code for external references. */
        private String customerCode;

        /** Customer type: SHIPPER, CONSIGNEE, FORWARDER, AGENT, CARRIER, TRUCK_COMPANY, RAIL_OPERATOR, OTHER. */
        private String customerType;

        /** Tax/VAT identification number — required on invoices. */
        private String taxIdentificationNumber;

        /** Default payment terms for this customer, e.g. "NET30". */
        private String paymentTermsDefault;

        /** Maximum outstanding balance allowed (credit limit). */
        private BigDecimal creditLimit;

        /** How invoices should be delivered: EMAIL, EDI, PORTAL, PAPER. */
        private String invoiceDeliveryMethod;

        /** Email or EDI address for electronic invoice delivery. */
        private String invoiceElectronicAddress;

        /** EDI partner identifier for automated message exchange. */
        private String ediPartnerCode;

        /** IBAN for payment processing. */
        private String iban;

        /** Bank SWIFT/BIC code. */
        private String swiftCode;

        /** Bank account number (alternative to IBAN for non-SEPA). */
        private String bankAccountNumber;

        /** Bank name. */
        private String bankName;

        /** Internal credit rating: A, B, C, D, BLOCKED. */
        private String creditRating;

        /** Whether this customer is approved to ship dangerous goods. */
        private boolean hazmatApproved;

        /** Whether this customer is approved to ship refrigerated cargo. */
        private boolean reeferApproved;

        /** Default currency for invoicing this customer. */
        private String defaultCurrency;

        /** Parent company ID for corporate hierarchy. */
        private String parentCompanyId;


        public ThirdParty() {
                super();
                this.status = Status.ACTIVE;
                this.version = 0L;
                this.updatedAt = Instant.now();
        }

        public String getCustomerCode() { return customerCode; }
        public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }

        public String getCustomerType() { return customerType; }
        public void setCustomerType(String customerType) { this.customerType = customerType; }

        public String getTaxIdentificationNumber() { return taxIdentificationNumber; }
        public void setTaxIdentificationNumber(String taxIdentificationNumber) { this.taxIdentificationNumber = taxIdentificationNumber; }

        public String getPaymentTermsDefault() { return paymentTermsDefault; }
        public void setPaymentTermsDefault(String paymentTermsDefault) { this.paymentTermsDefault = paymentTermsDefault; }

        public BigDecimal getCreditLimit() { return creditLimit; }
        public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }

        public String getInvoiceDeliveryMethod() { return invoiceDeliveryMethod; }
        public void setInvoiceDeliveryMethod(String invoiceDeliveryMethod) { this.invoiceDeliveryMethod = invoiceDeliveryMethod; }

        public String getInvoiceElectronicAddress() { return invoiceElectronicAddress; }
        public void setInvoiceElectronicAddress(String invoiceElectronicAddress) { this.invoiceElectronicAddress = invoiceElectronicAddress; }

        public String getEdiPartnerCode() { return ediPartnerCode; }
        public void setEdiPartnerCode(String ediPartnerCode) { this.ediPartnerCode = ediPartnerCode; }

        public String getIban() { return iban; }
        public void setIban(String iban) { this.iban = iban; }

        public String getSwiftCode() { return swiftCode; }
        public void setSwiftCode(String swiftCode) { this.swiftCode = swiftCode; }

        public String getBankAccountNumber() { return bankAccountNumber; }
        public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }

        public String getBankName() { return bankName; }
        public void setBankName(String bankName) { this.bankName = bankName; }

        public String getCreditRating() { return creditRating; }
        public void setCreditRating(String creditRating) { this.creditRating = creditRating; }

        public boolean isHazmatApproved() { return hazmatApproved; }
        public void setHazmatApproved(boolean hazmatApproved) { this.hazmatApproved = hazmatApproved; }

        public boolean isReeferApproved() { return reeferApproved; }
        public void setReeferApproved(boolean reeferApproved) { this.reeferApproved = reeferApproved; }

        public String getDefaultCurrency() { return defaultCurrency; }
        public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }

        public String getParentCompanyId() { return parentCompanyId; }
        public void setParentCompanyId(String parentCompanyId) { this.parentCompanyId = parentCompanyId; }
}
