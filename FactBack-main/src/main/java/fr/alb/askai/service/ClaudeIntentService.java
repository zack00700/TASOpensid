package fr.alb.askai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alb.ai.AnthropicClient;
import fr.alb.ai.AnthropicRequest;
import fr.alb.ai.AnthropicResponse;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Uses Claude (Anthropic) to understand a natural-language question and return
 * a structured {@link ClaudeIntent} describing which collection to query and
 * which analysis type to run.
 *
 * Falls back gracefully (returns {@code null}) if Claude is unavailable or
 * the response cannot be parsed, so the keyword-based fallback stays active.
 */
@ApplicationScoped
public class ClaudeIntentService {

    private static final Logger LOG = Logger.getLogger(ClaudeIntentService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Paris");

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a business intelligence assistant for a port terminal billing system.
            Given a user question, decide what analysis to run and respond with ONLY a JSON object — no markdown, no explanation.

            Current date: %s (Europe/Paris).
            Use this as the reference when the question mentions a relative period ("this year", "last month",
            "cette année", "le mois dernier", etc.). Always compute explicit YYYY-MM-DD bounds from it.
            Examples based on the current date above:
            - "this year" / "cette année" → from = first day of the current year, to = last day of the current year
            - "last month" / "le mois dernier" → full calendar range of the previous month
            - "last 30 days" → from = current date minus 30 days, to = current date

            Available collections:
            - INVOICE: invoices — fields: finalNumber, createdDate, customerName, amount, status
            - BILL_OF_LADING: bills of lading — fields: blNumber, createdAt, shipper, consignee, vessel, voyage, portOfLoading, portOfDischarge, eta, etd, status

            Analysis types (choose one that best fits the question):
            - COUNT            : count total number of records
            - CUSTOMER         : group by customer / shipper / consignee (bar chart)
            - MONTHLY_TREND    : evolution over time grouped by month (line chart)
            - STATUS           : distribution by status (pie chart)
            - TOTALS           : sum of monetary amounts (KPI)
            - PORT_OF_DISCHARGE: group by destination port — BILL_OF_LADING only
            - PORT_OF_LOADING  : group by origin port — BILL_OF_LADING only
            - HAZARDOUS        : hazardous/dangerous cargo analysis — BILL_OF_LADING only

            Date extraction: if the question mentions a specific period (relative or absolute), extract explicit
            YYYY-MM-DD bounds. If the question does not mention any period, return null for both "from" and "to".

            Respond with exactly this JSON schema:
            {
              "collection": "INVOICE" | "BILL_OF_LADING",
              "analysisType": "<one of the types above>",
              "from": "YYYY-MM-DD" | null,
              "to": "YYYY-MM-DD" | null,
              "summary": "<one sentence describing what will be shown>"
            }
            """;

    @RestClient
    AnthropicClient anthropicClient;

    @ConfigProperty(name = "anthropic.api.model", defaultValue = "claude-haiku-4-5-20251001")
    String model;

    /**
     * Asks Claude to interpret the question and returns a {@link ClaudeIntent}.
     * Returns {@code null} on any failure so callers can fall back to keywords.
     */
    public ClaudeIntent detectIntent(String question) {
        try {
            AnthropicRequest req = new AnthropicRequest();
            req.model = model;
            req.maxTokens = 256;
            req.system = String.format(SYSTEM_PROMPT_TEMPLATE, LocalDate.now(DEFAULT_ZONE));
            req.messages = List.of(new AnthropicRequest.AnthropicMessage("user", question));

            AnthropicResponse response = anthropicClient.createMessage(req);
            String raw = response.getText().trim();

            // Strip markdown code fences in case Claude adds them
            if (raw.startsWith("```")) {
                raw = raw.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();
            }

            ClaudeIntent intent = MAPPER.readValue(raw, ClaudeIntent.class);
            LOG.infof("Claude intent: collection=%s analysisType=%s from=%s to=%s",
                    intent.collection, intent.analysisType, intent.from, intent.to);
            return intent;

        } catch (Exception e) {
            LOG.warnf("Claude intent detection failed (%s) — falling back to keyword detection", e.getMessage());
            return null;
        }
    }
}
