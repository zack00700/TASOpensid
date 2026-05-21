package fr.alb.askai.service;

/**
 * Structured intent returned by Claude for a given Ask AI question.
 */
public class ClaudeIntent {
    /** "INVOICE" or "BILL_OF_LADING" */
    public String collection;

    /**
     * Analysis type matching the target analyzer's AnalysisType enum:
     * COUNT | CUSTOMER | MONTHLY_TREND | STATUS | TOTALS
     * + BOL-only: PORT_OF_DISCHARGE | PORT_OF_LOADING | HAZARDOUS
     */
    public String analysisType;

    /** ISO date "YYYY-MM-DD" or null */
    public String from;

    /** ISO date "YYYY-MM-DD" or null */
    public String to;

    /** One-sentence description Claude generated for the result */
    public String summary;
}
