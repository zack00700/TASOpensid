package fr.alb.askai.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@ApplicationScoped
public class TableDetectionService {

    private static final Map<String, List<String>> TABLE_KEYWORDS = Map.of(
            "INVOICE", List.of("invoice", "invoices", "bill", "billing", "payment", "customer", "amount"),
            "BILL_OF_LADING", List.of("shipment", "shipping", "cargo", "freight", "lading",
                    "consignment", "port", "vessel", "container", "shipper", "consignee",
                    "commodity", "bol", "bl", "eta", "etd", "loading", "discharge",
                    "terminal", "berth", "voyage", "hazardous")
    );

    private static final Map<String, Pattern> EXPLICIT_PATTERNS = Map.of(
            "BILL_OF_LADING", Pattern.compile("\\b(bill of lading|bol|b/l)\\b", Pattern.CASE_INSENSITIVE),
            "INVOICE", Pattern.compile("\\b(invoice|inv)\\b", Pattern.CASE_INSENSITIVE)
    );

    public String detectTable(String question) {
        String lowerQuestion = question.toLowerCase();

        // First try explicit pattern matching
        for (Map.Entry<String, Pattern> entry : EXPLICIT_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(question).find()) {
                return entry.getKey();
            }
        }

        // Then try keyword scoring
        Map<String, Integer> scores = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : TABLE_KEYWORDS.entrySet()) {
            int score = entry.getValue().stream()
                    .mapToInt(keyword -> lowerQuestion.contains(keyword) ? 1 : 0)
                    .sum();
            scores.put(entry.getKey(), score);
        }

        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .orElse("INVOICE"); // Default fallback
    }
}
