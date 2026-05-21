package fr.alb.ais.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AisEnvelope {

    @JsonProperty("MessageType")
    public String messageType;

    @JsonProperty("MetaData")
    public AisMetadata metadata;

    @JsonProperty("Message")
    public JsonNode message;
}
