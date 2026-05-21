package fr.alb.edi.model;

import fr.alb.model.EntityBase;

import java.time.Instant;
import java.util.List;

import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Trading partner (shipping line, customs authority, agent) that exchanges
 * EDI messages with the terminal.
 *
 * Stored in the EDI_PARTNER collection.
 */
@MongoEntity(collection = "EDI_PARTNER")
public class EdiPartner extends EntityBase {

    private static final long serialVersionUID = 1L;

    /** Unique short code identifying the partner (e.g. "MAERSK", "MSC"). */
    private String partnerId;

    /** Full display name of the partner organisation. */
    private String partnerName;

    /** Message flow direction relative to the terminal: INBOUND, OUTBOUND, or BOTH. */
    private String direction;

    /** EDI message types this partner is authorised to exchange (e.g. COPRAR, CUSCAR, BAPLTE). */
    private List<String> allowedMessageTypes;

    /** EDI dialect used by this partner: EDIFACT, X12, JSON, or CSV. */
    private String format;

    /** Destination URL or e-mail address for outbound messages. */
    private String endpoint;

    /** API key or bearer token used to authenticate outbound calls. */
    private String authToken;

    /** Whether this partner configuration is currently active. */
    private boolean active = true;

    /** Free-text operational notes about this partner. */
    private String notes;

    /** Timestamp of the most recent message exchanged with this partner. */
    private Instant lastMessageDate;

    /** Running total of messages exchanged with this partner. */
    private long messageCount;

    public EdiPartner() {
        super();
    }

    public String getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(String partnerId) {
        this.partnerId = partnerId;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public void setPartnerName(String partnerName) {
        this.partnerName = partnerName;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public List<String> getAllowedMessageTypes() {
        return allowedMessageTypes;
    }

    public void setAllowedMessageTypes(List<String> allowedMessageTypes) {
        this.allowedMessageTypes = allowedMessageTypes;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getLastMessageDate() {
        return lastMessageDate;
    }

    public void setLastMessageDate(Instant lastMessageDate) {
        this.lastMessageDate = lastMessageDate;
    }

    public long getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(long messageCount) {
        this.messageCount = messageCount;
    }
}
