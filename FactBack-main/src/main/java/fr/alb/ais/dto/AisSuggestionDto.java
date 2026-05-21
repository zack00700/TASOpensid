package fr.alb.ais.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Transport DTO for GET /api/ais/by-visit/{id}.
 * Fields are nullable — the resource emits 204 if none of suggestedEta,
 * suggestedAta, or position are populated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AisSuggestionDto {

    public Instant suggestedEta;
    public Instant suggestedAta;
    public Instant sourceTimestamp;
    public Integer navStatus;
    public Position position;

    public static class Position {
        public Double lat;
        public Double lon;
    }
}
