package fr.alb.ais.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipStaticData {

    @JsonProperty("UserID")
    public Long userId;

    @JsonProperty("Name")
    public String name;

    @JsonProperty("CallSign")
    public String callSign;

    @JsonProperty("ImoNumber")
    public Long imoNumber;

    @JsonProperty("Destination")
    public String destination;

    @JsonProperty("Eta")
    public Eta eta;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Eta {
        @JsonProperty("Month")
        public Integer month;
        @JsonProperty("Day")
        public Integer day;
        @JsonProperty("Hour")
        public Integer hour;
        @JsonProperty("Minute")
        public Integer minute;
    }
}
