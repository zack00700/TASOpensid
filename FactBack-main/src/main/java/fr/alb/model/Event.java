package fr.alb.model;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class Event {
	
	private static final String EVENT_IN = "IN";
	private static final String EVENT_OUT = "OUT";
	private static final String EVENT_INTERMEDIATE = "INTERMEDIATE";


	private String type;
	private Date timeStamp;
	private String location;
	private String notes;
	private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();
	
	public Event() {
		super();
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Date getTimeStamp() {
		return timeStamp;
	}

	public void setTimeStamp(Date timeStamp) {
		this.timeStamp = timeStamp;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public Map<String, Object> getAdditionalProperties() {
		return additionalProperties;
	}

	public void setAdditionalProperties(Map<String, Object> additionalProperties) {
		this.additionalProperties = additionalProperties;
	}

	@Override
	public String toString() {
		return "Event [type=" + type + ", timeStamp=" + timeStamp + ", location=" + location + ", notes="
				+ notes + ", additionalProperties=" + additionalProperties + "]";
	}
	
	public void validateEventType() throws Exception {
	    if (!this.getType().equals(EVENT_IN) 
	    	&& !this.getType().equals(EVENT_OUT)
	    	&& !this.getType().equals(EVENT_INTERMEDIATE)
	    		) {
	        throw new Exception("Invalid event type");
	    }
	}

	
}
