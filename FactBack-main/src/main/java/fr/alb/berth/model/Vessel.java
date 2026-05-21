package fr.alb.berth.model;

import fr.alb.model.EntityBase;

import java.util.LinkedHashMap;
import java.util.Map;

import fr.alb.type.Status;
import fr.alb.type.VesselType;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection= "VESSEL")
public class Vessel extends EntityBase {

	public static final long serialVersionUID = 1L;
	
	public String name;	
	public String imoNumber;
	public String mmsi;
	public String callSign;
	public String flag;
	public String owner;
	public String operator;
	public VesselType vesselType;
	public Status status;
	public Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();
	
	
	public Vessel() {
		super();
	}	
	
}
