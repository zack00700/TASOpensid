package fr.alb.bol.model;

import fr.alb.model.EntityBase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonIgnore;

import fr.alb.yard.model.Commodity;
import fr.alb.yard.model.Item;
import fr.alb.model.TransportInfo;
import fr.alb.type.BillOfLadingType;

public class BillOfLading extends EntityBase {
    private final static long serialVersionUID = -5654272183543651512L;

    private String blNumber;
    private BillOfLadingType blType;
    private String shipper;
    private String consignee;
    private String notifyParty;
    private String vessel;
    private String voyage;
    private String portOfLoading;
    private String portOfDischarge;
    private String placeOfDelivery;
    private Commodity commodity;
    @BsonIgnore
    private List<Item> items;
    private List<String> itemIds;
    private Instant createdAt;
    private Instant updatedAt;
    private TransportInfo transport;

    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();
    private String status;
    private String transportType;
    private String driver;
    private String trainNumber;
    private String truckNumber;

    // === Missing BOL fields for COPRAR/CUSCAR compliance ===

    /** Date of BOL issuance. */
    private LocalDate bolDate;

    /** BOL status: ISSUED, SURRENDERED, CANCELLED, AMENDED. */
    private String bolStatus; // default "ISSUED"

    /** Original BOL reference if this is an amendment. */
    private String originalBolId;

    /** Freight forwarder's House BOL number. */
    private String houseBolNumber;

    /** Carrier's Master BOL number. */
    private String masterBolNumber;

    /** Vessel booking reference number. */
    private String bookingNumber;

    /** Shipping line / carrier name. */
    private String shippingLine;

    /** INCOTERMS 2020 term (FOB, CIF, EXW, DAP, etc.). */
    private String incoterms;

    /** Where freight charges are payable: PREPAID, COLLECT, BOTH. */
    private String freightPayableAt;

    /** Total freight charges amount. */
    private BigDecimal freightCharges;

    /** Currency of freight charges. */
    private String freightCurrency;

    /** When the vessel sailed (on-board date). */
    private Instant onBoardDate;

    /** Transshipment port if applicable. */
    private String transshipmentPort;

    /** HS (Harmonized System) commodity codes for customs. */
    private List<String> hsCodes;

    /** Country of origin of goods. */
    private String countryOfOrigin;

    /** EDI reference — ID of the COPRAR/BAPLTE message that created this BOL. */
    private String ediMessageId;

    /** Whether all required documentation is complete. */
    private boolean documentationComplete;


    public BillOfLading() {
        super();
        this.bolStatus = "ISSUED";
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public String getBlNumber() {
        return blNumber;
    }

    public void setBlNumber(String blNumber) {
        this.blNumber = blNumber;
    }

    public BillOfLadingType getBlType() {
        return blType;
    }

    public void setBlType(BillOfLadingType blType) {
        this.blType = blType;
    }

    public String getShipper() {
        return shipper;
    }

    public void setShipper(String shipper) {
        this.shipper = shipper;
    }

    public String getConsignee() {
        return consignee;
    }

    public void setConsignee(String consignee) {
        this.consignee = consignee;
    }

    public String getNotifyParty() {
        return notifyParty;
    }

    public void setNotifyParty(String notifyParty) {
        this.notifyParty = notifyParty;
    }

    public String getVoyage() {
        return voyage;
    }

    public void setVoyage(String voyage) {
        this.voyage = voyage;
    }

    public String getPortOfLoading() {
        return portOfLoading;
    }

    public void setPortOfLoading(String portOfLoading) {
        this.portOfLoading = portOfLoading;
    }

    public String getPortOfDischarge() {
        return portOfDischarge;
    }

    public void setPortOfDischarge(String portOfDischarge) {
        this.portOfDischarge = portOfDischarge;
    }

    public String getPlaceOfDelivery() {
        return placeOfDelivery;
    }

    public void setPlaceOfDelivery(String placeOfDelivery) {
        this.placeOfDelivery = placeOfDelivery;
    }

    public Commodity getCommodity() {
        return commodity;
    }

    public void setCommodity(Commodity commodity) {
        this.commodity = commodity;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public String getVessel() {
        return vessel;
    }


    public void setVessel(String vessel) {
        this.vessel = vessel;
    }


    public List<Item> getItems() {
        return items;
    }


    public void setItems(List<Item> items) {
        this.items = items;
    }

    public List<String> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<String> itemIds) {
        this.itemIds = itemIds;
    }


    public Instant getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }


    public Instant getUpdatedAt() {
        return updatedAt;
    }


    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public TransportInfo getTransport() {
        return transport;
    }

    public void setTransport(TransportInfo transport) {
        this.transport = transport;
    }


    @Override
    public String toString() {
        return "BillOfLading [blNumber=" + blNumber + ", blType=" + blType + ", shipper=" + shipper + ", consignee="
                + consignee + ", notifyParty=" + notifyParty + ", vessel=" + vessel + ", voyage=" + voyage
                + ", portOfLoading=" + portOfLoading + ", portOfDischarge=" + portOfDischarge + ", placeOfDelivery="
                + placeOfDelivery + ", commodity=" + commodity + ", itemIds=" + itemIds + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + ", transport=" + transport + ", additionalProperties=" + additionalProperties + "]";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }


    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }


    public String getTruckNumber() {
        return truckNumber;
    }

    public void setTruckNumber(String truckNumber) {
        this.truckNumber = truckNumber;
    }

    public LocalDate getBolDate() { return bolDate; }
    public void setBolDate(LocalDate bolDate) { this.bolDate = bolDate; }

    public String getBolStatus() { return bolStatus; }
    public void setBolStatus(String bolStatus) { this.bolStatus = bolStatus; }

    public String getOriginalBolId() { return originalBolId; }
    public void setOriginalBolId(String originalBolId) { this.originalBolId = originalBolId; }

    public String getHouseBolNumber() { return houseBolNumber; }
    public void setHouseBolNumber(String houseBolNumber) { this.houseBolNumber = houseBolNumber; }

    public String getMasterBolNumber() { return masterBolNumber; }
    public void setMasterBolNumber(String masterBolNumber) { this.masterBolNumber = masterBolNumber; }

    public String getBookingNumber() { return bookingNumber; }
    public void setBookingNumber(String bookingNumber) { this.bookingNumber = bookingNumber; }

    public String getShippingLine() { return shippingLine; }
    public void setShippingLine(String shippingLine) { this.shippingLine = shippingLine; }

    public String getIncoterms() { return incoterms; }
    public void setIncoterms(String incoterms) { this.incoterms = incoterms; }

    public String getFreightPayableAt() { return freightPayableAt; }
    public void setFreightPayableAt(String freightPayableAt) { this.freightPayableAt = freightPayableAt; }

    public BigDecimal getFreightCharges() { return freightCharges; }
    public void setFreightCharges(BigDecimal freightCharges) { this.freightCharges = freightCharges; }

    public String getFreightCurrency() { return freightCurrency; }
    public void setFreightCurrency(String freightCurrency) { this.freightCurrency = freightCurrency; }

    public Instant getOnBoardDate() { return onBoardDate; }
    public void setOnBoardDate(Instant onBoardDate) { this.onBoardDate = onBoardDate; }

    public String getTransshipmentPort() { return transshipmentPort; }
    public void setTransshipmentPort(String transshipmentPort) { this.transshipmentPort = transshipmentPort; }

    public List<String> getHsCodes() { return hsCodes; }
    public void setHsCodes(List<String> hsCodes) { this.hsCodes = hsCodes; }

    public String getCountryOfOrigin() { return countryOfOrigin; }
    public void setCountryOfOrigin(String countryOfOrigin) { this.countryOfOrigin = countryOfOrigin; }

    public String getEdiMessageId() { return ediMessageId; }
    public void setEdiMessageId(String ediMessageId) { this.ediMessageId = ediMessageId; }

    public boolean isDocumentationComplete() { return documentationComplete; }
    public void setDocumentationComplete(boolean documentationComplete) { this.documentationComplete = documentationComplete; }
}