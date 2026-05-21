package fr.alb.ais.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PositionReport {

    @JsonProperty("UserID")
    public Long userId;

    @JsonProperty("Latitude")
    public Double latitude;

    @JsonProperty("Longitude")
    public Double longitude;

    @JsonProperty("Sog")
    public Double sog;

    @JsonProperty("Cog")
    public Double cog;

    @JsonProperty("TrueHeading")
    public Double trueHeading;

    @JsonProperty("NavigationalStatus")
    public Integer navigationalStatus;
}
