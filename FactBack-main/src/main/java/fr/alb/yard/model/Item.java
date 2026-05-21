package fr.alb.yard.model;

import fr.alb.model.EntityBase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import fr.alb.yard.model.Lifecycle;
import fr.alb.yard.model.OogDimensions;
import fr.alb.type.CustomsStatus;
import fr.alb.type.EmptyStatus;
import fr.alb.type.FreightKind;
import fr.alb.type.ItemCategory;
import fr.alb.type.LifeCycleStatus;
import fr.alb.type.ItemStatus;
import fr.alb.type.ItemType;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "ITEM")
public class Item extends EntityBase {

    private static final long serialVersionUID = 1L;

    private ItemType itemType;
    private String itemNumber;
    private String type;
    private String ownerId;
    private String position;
    private ItemStatus itemStatus;
    private Date lastInspectionDate, nextInspectionDate;
    private String notes;
    private String status;
    private List<String> lifeCycles = new ArrayList<String>();
    private String relatedInvoice;
    private String billOfLadingId;
    private Double weight;
    private Double volume;
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    // === Port/Container fields ===

    /** ISO 11161 container ID (e.g. CSQU3054383). */
    private String containerNumber;

    /** Container size/type: 20FT, 40FT, 40HC, 45HC, REEFER_20, REEFER_40, OPEN_TOP, FLAT_RACK, TANK */
    private String containerType;

    /** Customs/shipper seal numbers on this container. */
    private List<String> sealNumbers;

    /** Whether cargo is dangerous goods (IMDG). */
    private boolean hazmatFlag;

    /** IMDG hazmat class (1-9, e.g. "3" = flammable liquid). */
    private String hazmatClass;

    /** UN number for dangerous goods (e.g. "UN1203"). */
    private String unNumber;

    /** Whether container requires refrigeration. */
    private boolean reeferFlag;

    /** Required temperature in Celsius for reefer containers. */
    private Double reeferTemperature;

    /** Out of Gauge flag — container exceeds standard dimensions. */
    private boolean oogFlag;

    /** OOG dimension overages (only populated when oogFlag=true). */
    private OogDimensions oogDimensions;

    /** Whether Verified Gross Mass (VGM) has been received (SOLAS mandatory). */
    private boolean weightVerified;

    /** Verified gross mass in kg (different from declared weight). */
    private Double verifiedWeight;

    /** Whether container is FULL or EMPTY (critical for billing). */
    private EmptyStatus emptyStatus;

    /** Physical condition of the container. */
    private String condition; // GOOD, DAMAGED, NEEDS_REPAIR

    /** ISO 3874 damage codes (e.g. BENT, DENT, HOLE). */
    private List<String> damageCodes;

    /** Customs clearance status. */
    private CustomsStatus customsStatus;

    /** Date/time container entered the terminal (gate-in). */
    private Instant gateInDate;

    /** Date/time container exited the terminal (gate-out). */
    private Instant gateOutDate;

    /** Inbound voyage reference (arrival). */
    private String inboundVoyage;

    /** Outbound voyage reference (departure). */
    private String outboundVoyage;

    /** When storage charges start (after grace period). */
    private Instant chargingStartDate;

    /** When free days expire. */
    private Instant gracePeriodExpiryDate;

    /** Special handling instructions. */
    private String handlingCode;

    // === Billing / commercial fields ===

    /** Import, Export or Transshipment — critical for tariff differentiation. */
    private ItemCategory category;

    /** Nature of the freight: FCL, LCL, Empty, Breakbulk, Ro-Ro. */
    private FreightKind freightKind;

    /** Booking number (shipping order reference). */
    private String bookingNumber;

    /** Name of the consignee (cargo receiver). */
    private String consigneeName;

    /** Name of the shipper (cargo sender). */
    private String shipperName;

    /** Reference to dangerous goods declaration document. */
    private String dangerousGoodsDeclarationRef;

    /** Harmonised System (HS) commodity code — populated from CUSCAR. */
    private String hsCode;

    /** ISO 3166-1 alpha-2 country of origin — populated from CUSCAR. */
    private String countryOfOrigin;

    public Item() {
        super();
    }

    public Item(ItemType itemType, String itemNumber, String type, String ownerId, String position,
                ItemStatus itemStatus, Date lastInspectionDate, Date nextInspectionDate, String notes, String status,
                List<String> lifeCycles, String relatedInvoice, String billOfLadingId, Double weight, Double volume,
                Map<String, Object> additionalProperties) {
        super();
        this.itemType = itemType;
        this.itemNumber = itemNumber;
        this.type = type;
        this.ownerId = ownerId;
        this.position = position;
        this.itemStatus = itemStatus;
        this.lastInspectionDate = lastInspectionDate;
        this.nextInspectionDate = nextInspectionDate;
        this.notes = notes;
        this.status = status;
        this.lifeCycles = lifeCycles;
        this.relatedInvoice = relatedInvoice;
        this.billOfLadingId = billOfLadingId;
        this.weight = weight;
        this.volume = volume;
        this.additionalProperties = additionalProperties;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public String getItemNumber() {
        return itemNumber;
    }

    public void setItemNumber(String itemNumber) {
        this.itemNumber = itemNumber;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public ItemStatus getItemStatus() {
        return itemStatus;
    }

    public void setItemStatus(ItemStatus itemStatus) {
        this.itemStatus = itemStatus;
    }

    public Date getLastInspectionDate() {
        return lastInspectionDate;
    }

    public void setLastInspectionDate(Date lastInspectionDate) {
        this.lastInspectionDate = lastInspectionDate;
    }

    public Date getNextInspectionDate() {
        return nextInspectionDate;
    }

    public void setNextInspectionDate(Date nextInspectionDate) {
        this.nextInspectionDate = nextInspectionDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getStatus() {
        if (status == null) {
            status = computeStatus();
        }
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String computeStatus() {
        // Makes one DB query (Lifecycle.findById) per item — callers processing
        // a list of items should use computeStatus(Map) to avoid N+1 queries.
        return computeStatus(Collections.emptyMap());
    }

    /**
     * Computes the item status using a pre-loaded lifecycle cache to avoid
     * issuing one DB query per item (N+1 problem).
     *
     * @param lifecycleCache a map of lifecycle-id → Lifecycle pre-fetched in batch
     *                       by the caller; if the required lifecycle is absent from
     *                       the map the method falls back to a live DB lookup.
     */
    public String computeStatus(Map<String, Lifecycle> lifecycleCache) {
        if (lifeCycles == null || lifeCycles.isEmpty()) {
            return "Preadvised";
        }
        String lastLcId = lifeCycles.get(lifeCycles.size() - 1);
        // Use the pre-loaded cache; fall back to a live DB query only on a miss.
        Lifecycle lc = lifecycleCache.getOrDefault(lastLcId, null);
        if (lc == null) {
            lc = Lifecycle.findById(lastLcId);
        }
        if (lc == null || lc.getEventIds() == null || lc.getEventIds().isEmpty()) {
            return "Preadvised";
        }
        LifeCycleStatus st = lc.getStatus();
        if (st == LifeCycleStatus.IN_PROGRESS) {
            return "In Yard";
        }
        if (st == LifeCycleStatus.COMPLETED) {
            return "Delivered";
        }
        return status;
    }

    public List<String> getLifeCycles() {
        return lifeCycles;
    }

    public void setLifeCycles(List<String> lifeCycles) {
        this.lifeCycles = lifeCycles;
    }

    public String getRelatedInvoice() {
        return relatedInvoice;
    }

    public void setRelatedInvoice(String relatedInvoice) {
        this.relatedInvoice = relatedInvoice;
    }

    public String getBillOfLadingId() {
        return billOfLadingId;
    }

    public void setBillOfLadingId(String billOfLadingId) {
        this.billOfLadingId = billOfLadingId;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Double getVolume() {
        return volume;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }


    public String getContainerNumber() {
        return containerNumber;
    }

    public void setContainerNumber(String containerNumber) {
        this.containerNumber = containerNumber;
    }

    public String getContainerType() {
        return containerType;
    }

    public void setContainerType(String containerType) {
        this.containerType = containerType;
    }

    public List<String> getSealNumbers() {
        return sealNumbers;
    }

    public void setSealNumbers(List<String> sealNumbers) {
        this.sealNumbers = sealNumbers;
    }

    public boolean isHazmatFlag() {
        return hazmatFlag;
    }

    public void setHazmatFlag(boolean hazmatFlag) {
        this.hazmatFlag = hazmatFlag;
    }

    public String getHazmatClass() {
        return hazmatClass;
    }

    public void setHazmatClass(String hazmatClass) {
        this.hazmatClass = hazmatClass;
    }

    public String getUnNumber() {
        return unNumber;
    }

    public void setUnNumber(String unNumber) {
        this.unNumber = unNumber;
    }

    public boolean isReeferFlag() {
        return reeferFlag;
    }

    public void setReeferFlag(boolean reeferFlag) {
        this.reeferFlag = reeferFlag;
    }

    public Double getReeferTemperature() {
        return reeferTemperature;
    }

    public void setReeferTemperature(Double reeferTemperature) {
        this.reeferTemperature = reeferTemperature;
    }

    public boolean isOogFlag() {
        return oogFlag;
    }

    public void setOogFlag(boolean oogFlag) {
        this.oogFlag = oogFlag;
    }

    public OogDimensions getOogDimensions() {
        return oogDimensions;
    }

    public void setOogDimensions(OogDimensions oogDimensions) {
        this.oogDimensions = oogDimensions;
    }

    public boolean isWeightVerified() {
        return weightVerified;
    }

    public void setWeightVerified(boolean weightVerified) {
        this.weightVerified = weightVerified;
    }

    public Double getVerifiedWeight() {
        return verifiedWeight;
    }

    public void setVerifiedWeight(Double verifiedWeight) {
        this.verifiedWeight = verifiedWeight;
    }

    public EmptyStatus getEmptyStatus() {
        return emptyStatus;
    }

    public void setEmptyStatus(EmptyStatus emptyStatus) {
        this.emptyStatus = emptyStatus;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public List<String> getDamageCodes() {
        return damageCodes;
    }

    public void setDamageCodes(List<String> damageCodes) {
        this.damageCodes = damageCodes;
    }

    public CustomsStatus getCustomsStatus() {
        return customsStatus;
    }

    public void setCustomsStatus(CustomsStatus customsStatus) {
        this.customsStatus = customsStatus;
    }

    public Instant getGateInDate() {
        return gateInDate;
    }

    public void setGateInDate(Instant gateInDate) {
        this.gateInDate = gateInDate;
    }

    public Instant getGateOutDate() {
        return gateOutDate;
    }

    public void setGateOutDate(Instant gateOutDate) {
        this.gateOutDate = gateOutDate;
    }

    public String getInboundVoyage() {
        return inboundVoyage;
    }

    public void setInboundVoyage(String inboundVoyage) {
        this.inboundVoyage = inboundVoyage;
    }

    public String getOutboundVoyage() {
        return outboundVoyage;
    }

    public void setOutboundVoyage(String outboundVoyage) {
        this.outboundVoyage = outboundVoyage;
    }

    public Instant getChargingStartDate() {
        return chargingStartDate;
    }

    public void setChargingStartDate(Instant chargingStartDate) {
        this.chargingStartDate = chargingStartDate;
    }

    public Instant getGracePeriodExpiryDate() {
        return gracePeriodExpiryDate;
    }

    public void setGracePeriodExpiryDate(Instant gracePeriodExpiryDate) {
        this.gracePeriodExpiryDate = gracePeriodExpiryDate;
    }

    public String getHandlingCode() {
        return handlingCode;
    }

    public void setHandlingCode(String handlingCode) {
        this.handlingCode = handlingCode;
    }

    public String getDangerousGoodsDeclarationRef() {
        return dangerousGoodsDeclarationRef;
    }

    public void setDangerousGoodsDeclarationRef(String dangerousGoodsDeclarationRef) {
        this.dangerousGoodsDeclarationRef = dangerousGoodsDeclarationRef;
    }

    public String getHsCode() {
        return hsCode;
    }

    public void setHsCode(String hsCode) {
        this.hsCode = hsCode;
    }

    public String getCountryOfOrigin() {
        return countryOfOrigin;
    }

    public void setCountryOfOrigin(String countryOfOrigin) {
        this.countryOfOrigin = countryOfOrigin;
    }

    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }

    public FreightKind getFreightKind() { return freightKind; }
    public void setFreightKind(FreightKind freightKind) { this.freightKind = freightKind; }

    public String getBookingNumber() { return bookingNumber; }
    public void setBookingNumber(String bookingNumber) { this.bookingNumber = bookingNumber; }

    public String getConsigneeName() { return consigneeName; }
    public void setConsigneeName(String consigneeName) { this.consigneeName = consigneeName; }

    public String getShipperName() { return shipperName; }
    public void setShipperName(String shipperName) { this.shipperName = shipperName; }

    @Override
    public String toString() {
        return "Item [itemType=" + itemType + ", itemNumber=" + itemNumber + ", type=" + type + ", ownerId=" + ownerId
                + ", position=" + position + ", itemStatus=" + itemStatus + ", lastInspectionDate=" + lastInspectionDate
                + ", nextInspectionDate=" + nextInspectionDate + ", notes=" + notes + ", status=" + status + ", lifeCycles=" + lifeCycles
                + ", billOfLadingId=" + billOfLadingId + ", weight=" + weight + ", volume=" + volume
                + ", additionalProperties=" + additionalProperties + "]";
    }


}
