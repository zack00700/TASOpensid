package fr.alb.askai.dto;

import java.util.Map;

/**
 * @param question         the raw user question
 * @param filters          optional key/value filters (e.g. date range, customer name)
 * @param analysisTypeHint optional analysis type hint from Claude (e.g. "MONTHLY_TREND").
 *                         When present, analyzers use this instead of keyword detection.
 */
public record UserQuery(String question, Map<String, Object> filters, String analysisTypeHint) {

    /** Backward-compatible constructor without hint (keyword detection will be used). */
    public UserQuery(String question, Map<String, Object> filters) {
        this(question, filters, null);
    }
}
