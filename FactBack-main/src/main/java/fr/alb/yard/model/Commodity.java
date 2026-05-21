package fr.alb.yard.model;

import fr.alb.model.EntityBase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonProperty;

public class Commodity extends EntityBase {
	private final static long serialVersionUID = 2391392579601730153L;
	private String description;
	
	@BsonProperty("weight_kg")
	private Double weightKg;
	
	@BsonProperty("volume_m3")
	private Double volumeM3;
	
	@BsonProperty("packages_number")
	private Double packagesNumber;
	
	private Boolean isHazardous;
	private String hazardClass_;
	private String unNumber;
	
	
	private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();
	
	
	
	public Commodity() {
		super();
	}
	
	public String getDescription() {
	return description;
	}
	
	public void setDescription(String description) {
	this.description = description;
	}
	
	public Double getWeightKg() {
	return weightKg;
	}
	
	public void setWeightKg(Double weightKg) {
	this.weightKg = weightKg;
	}
	
	public Double getVolumeM3() {
	return volumeM3;
	}
	
	public void setVolumeM3(Double volumeM3) {
	this.volumeM3 = volumeM3;
	}
	
	public Double getPackagesNumber() {
	return packagesNumber;
	}
	
	public void setPackagesNumber(Double packagesNumber) {
	this.packagesNumber = packagesNumber;
	}
	
	public Map<String, Object> getAdditionalProperties() {
	return this.additionalProperties;
	}
	
	
	
	public Boolean getIsHazardous() {
		return isHazardous;
	}

	public void setIsHazardous(Boolean isHazardous) {
		this.isHazardous = isHazardous;
	}

	public String getHazardClass_() {
		return hazardClass_;
	}

	public void setHazardClass_(String hazardClass_) {
		this.hazardClass_ = hazardClass_;
	}

	public String getUnNumber() {
		return unNumber;
	}

	public void setUnNumber(String unNumber) {
		this.unNumber = unNumber;
	}	
	
	public void setAdditionalProperties(Map<String, Object> additionalProperties) {
		this.additionalProperties = additionalProperties;
	}

	public void setAdditionalProperty(String name, Object value) {
	this.additionalProperties.put(name, value);
	}

}