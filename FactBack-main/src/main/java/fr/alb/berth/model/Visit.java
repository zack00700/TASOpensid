package fr.alb.berth.model;

import fr.alb.model.EntityBase;

import java.time.LocalDateTime;

import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection= "VESSEL_VISIT")
public class Visit extends EntityBase {
	private static final long serialVersionUID = 1L;
	
	public String vesselName;
    public String vesselId;
    public String visitReference;
    public String phase;
    public String service;
    public String serviceName;
    public String facility;
    
    public LocalDateTime eta;
    public LocalDateTime etd;
    public LocalDateTime ata;
    public LocalDateTime atd;
    
    public String pod;
    public String pol;
    public String finalDestination;
    
    public LocalDateTime beginReceive;
    public LocalDateTime dryCutoff;
    public LocalDateTime reeferCutoff;
    public LocalDateTime hazCutoff;
    public LocalDateTime emptyPickup;
    public String inboundVoyage;
    public String outboundVoyage;
    public String inboundCaptain;
    public String outboundCaptain;
    public String lineOperator;
    public String notes;
    
    public Visit() {
    	super();
    }
}
