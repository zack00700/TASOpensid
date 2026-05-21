package fr.alb.ais.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AisMetadata {

    @JsonProperty("MMSI")
    public Long mmsi;

    @JsonProperty("ShipName")
    public String shipName;

    @JsonProperty("latitude")
    public Double latitude;

    @JsonProperty("longitude")
    public Double longitude;

    @JsonProperty("time_utc")
    public String timeUtc;

    public Instant timestamp() {
        if (timeUtc == null) return null;
        try {
            String prefix = timeUtc.length() >= 19 ? timeUtc.substring(0, 19).replace(' ', 'T') : null;
            return prefix == null ? null : Instant.parse(prefix + "Z");
        } catch (Exception e) {
            return null;
        }
    }
}
