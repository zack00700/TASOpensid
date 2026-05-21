package fr.alb.askai.service;

import fr.alb.askai.dto.AnalysisResponse;
import fr.alb.askai.dto.UserQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.BadRequestException;
import org.bson.Document;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@ApplicationScoped
@Named("invoiceAnalyzer")
public class InvoiceAnalyzer extends AbstractAnalyzer {

    private static final Logger LOG = Logger.getLogger(InvoiceAnalyzer.class);
    private static final int TOP_N = 12;

    private enum AnalysisType {
        CUSTOMER,
        MONTHLY_TREND,
        STATUS,
        TOTALS,
        COUNT
    }

    @Override
    public AnalysisResponse analyze(UserQuery query, TableConfig cfg) {
        Objects.requireNonNull(cfg, "TableConfig is required");
        if (query == null || query.question() == null || query.question().isBlank()) {
            throw new BadRequestException("Question is required");
        }

        // Use Claude's hint if provided, fall back to keyword detection
        AnalysisType type = resolveAnalysisType(query);
        if (type == null) {
            throw new BadRequestException("Unsupported question for Invoice analysis");
        }

        Map<String, String> aliases = cfg.getFieldAliases();
        DateRange range = resolveDateRange(query, cfg);
        LOG.infof("Invoice analysis=%s range=%s -> %s", type, range.getFrom(), range.getTo());

        return switch (type) {
            case CUSTOMER -> byCustomer(query, cfg, range, aliases);
            case MONTHLY_TREND -> monthlyTrend(query, cfg, range, aliases);
            case STATUS -> statusDistribution(query, cfg, range, aliases);
            case TOTALS -> totals(query, cfg, range, aliases);
            case COUNT -> simpleCount(query, cfg, range, aliases);
        };
    }

    private AnalysisResponse simpleCount(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        LOG.infof("BillOfLading simple count analysis");

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(buildMatchStage(query, cfg, range, aliases));
        pipeline.add(new Document("$count", "total"));

        List<Document> docs = runPipeline(cfg, pipeline);
        long count = docs.isEmpty() ? 0L : toLong(docs.get(0).get("total"));

        // Format KPI
        List<String> labels = List.of("Total Invoices");
        List<Number> data = List.of(count);

        Map<String, Object> extras = baseExtras(query, range);
        extras.put("totalCount", count);

        String summary = String.format("Found %d %s", count,
                count == 1 ? cfg.getDisplayName() : cfg.getDisplayName() + "s");

        return new AnalysisResponse("kpi", labels,
                List.of(new AnalysisResponse.Dataset("Count", data)),
                summary, extras);
    }

    private AnalysisType resolveAnalysisType(UserQuery query) {
        if (query.analysisTypeHint() != null) {
            try {
                return AnalysisType.valueOf(query.analysisTypeHint());
            } catch (IllegalArgumentException ignored) {
                LOG.debugf("Unknown analysisTypeHint '%s' for Invoice, falling back to keyword detection",
                        query.analysisTypeHint());
            }
        }
        return detectAnalysis(query.question());
    }

    private AnalysisType detectAnalysis(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        if (containsAny(q, "status", "state", "stage")) {
            return AnalysisType.STATUS;
        }
        if (isSimpleCountQuestion(q)) {
            return AnalysisType.COUNT;
        }
        if (containsAny(q, "trend", "monthly", "over time", "per month", "timeline")) {
            return AnalysisType.MONTHLY_TREND;
        }
        if (containsAny(q, "customer", "client", "account", "buyer")) {
            return AnalysisType.CUSTOMER;
        }
        if (containsAny(q, "total amount", "total revenue", "total invoices", "overall amount", "sum", "turnover", "sales")) {
            return AnalysisType.TOTALS;
        }
        if (containsAny(q, "total")) {
            return AnalysisType.TOTALS;
        }
        return null;
    }

    private boolean isSimpleCountQuestion(String q) {

        return (containsAny(q, "how many", "count of", "number of") &&
                containsAny(q, "invoice", "invoices", "customer", "contract")) ||
                (containsAny(q, "total") &&
                        containsAny(q, "invoice", "invoices", "customer", "contract") &&
                        !containsAny(q, "weight", "volume", "packages", "cargo"));
    }
    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern COUNT_FOR_PATTERN =
            Pattern.compile("(?i)\\bhow\\s+many\\s+invoices?\\b(?:\\s+(?:for|of|to)\\s+([\\p{L}\\p{N} .'-]+))?");


    private AnalysisResponse byCustomer(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        String customerField = resolveField("customer", aliases);
        String amountField = resolveField("totalAmount", aliases);
        LOG.infof("Invoice by customer field=%s amount=%s", customerField, amountField);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(buildMatchStage(query, cfg, range, aliases));
        pipeline.add(new Document("$group", new Document("_id", new Document("$ifNull", List.of("$" + customerField, "Unknown")))
                .append("amount", new Document("$sum", new Document("$ifNull", List.of("$" + amountField, 0))))
                .append("count", new Document("$sum", 1))));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("label", "$_id")
                .append("amount", "$amount")
                .append("count", "$count")));
        pipeline.add(new Document("$sort", new Document("amount", -1)));

        List<Document> docs = runPipeline(cfg, pipeline);
        if (docs.isEmpty()) {
            return emptyResponse("bar", "Amount", cfg.getDisplayName() + " Amount by Customer", range, query);
        }

        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        BigDecimal othersAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalCount = 0L;

        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            BigDecimal amount = toBigDecimal(doc.get("amount"));
            long count = toLong(doc.get("count"));
            String label = labelOrUnknown(doc.get("label"));
            totalAmount = totalAmount.add(amount);
            totalCount += count;
            if (i < TOP_N) {
                labels.add(label);
                data.add(amount);
            } else {
                othersAmount = othersAmount.add(amount);
            }
        }
        if (othersAmount.compareTo(BigDecimal.ZERO) > 0) {
            labels.add("Others");
            data.add(othersAmount);
        }

        Map<String, Object> extras = baseExtras(query, range);
        extras.put("groupField", customerField);
        extras.put("amountField", amountField);
        extras.put("totalAmount", totalAmount);
        extras.put("totalCount", totalCount);
        if (othersAmount.compareTo(BigDecimal.ZERO) > 0) {
            extras.put("othersAmount", othersAmount);
        }

        return new AnalysisResponse("bar", labels,
                List.of(new AnalysisResponse.Dataset("Amount", data)),
                cfg.getDisplayName() + " Amount by Customer", extras);
    }

    private AnalysisResponse monthlyTrend(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        String dateField = cfg.getDateField();
        String amountField = resolveField("totalAmount", aliases);
        LOG.infof("Invoice monthly trend date=%s amount=%s", dateField, amountField);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(buildMatchStage(query, cfg, range, aliases));
        Document dateToString = new Document("$dateToString", new Document("format", "%Y-%m")
                .append("date", "$" + dateField)
                .append("timezone", DEFAULT_ZONE.getId()));
        pipeline.add(new Document("$group", new Document("_id", dateToString)
                .append("amount", new Document("$sum", new Document("$ifNull", List.of("$" + amountField, 0))))
                .append("count", new Document("$sum", 1))));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("label", "$_id")
                .append("amount", "$amount")
                .append("count", "$count")));
        pipeline.add(new Document("$sort", new Document("label", 1)));

        List<Document> docs = runPipeline(cfg, pipeline);
        if (docs.isEmpty()) {
            return emptyResponse("line", "Amount", cfg.getDisplayName() + " Monthly Amount", range, query);
        }

        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalCount = 0L;
        for (Document doc : docs) {
            String label = labelOrUnknown(doc.get("label"));
            BigDecimal amount = toBigDecimal(doc.get("amount"));
            long count = toLong(doc.get("count"));
            amounts.put(label, amount);
            totalAmount = totalAmount.add(amount);
            totalCount += count;
        }

        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        ZonedDateTime cursor = monthStart(range.getFrom());
        ZonedDateTime end = inclusiveMonthlyEnd(range);
        while (!cursor.isAfter(end)) {
            String label = String.format("%d-%02d", cursor.getYear(), cursor.getMonthValue());
            labels.add(label);
            data.add(amounts.getOrDefault(label, BigDecimal.ZERO));
            cursor = cursor.plusMonths(1);
        }

        Map<String, Object> extras = baseExtras(query, range);
        extras.put("dateField", dateField);
        extras.put("amountField", amountField);
        extras.put("totalAmount", totalAmount);
        extras.put("totalCount", totalCount);

        return new AnalysisResponse("line", labels,
                List.of(new AnalysisResponse.Dataset("Amount", data)),
                cfg.getDisplayName() + " Monthly Amount", extras);
    }

    private AnalysisResponse statusDistribution(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        String statusField = resolveField("status", aliases);
        String amountField = resolveField("totalAmount", aliases);
        LOG.infof("Invoice status distribution status=%s amount=%s", statusField, amountField);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(buildMatchStage(query, cfg, range, aliases));
        pipeline.add(new Document("$group", new Document("_id", new Document("$ifNull", List.of("$" + statusField, "Unknown")))
                .append("count", new Document("$sum", 1))
                .append("amount", new Document("$sum", new Document("$ifNull", List.of("$" + amountField, 0))))));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("label", "$_id")
                .append("count", "$count")
                .append("amount", "$amount")));
        pipeline.add(new Document("$sort", new Document("count", -1)));

        List<Document> docs = runPipeline(cfg, pipeline);
        if (docs.isEmpty()) {
            return emptyResponse("pie", "Invoices", cfg.getDisplayName() + " Status", range, query);
        }

        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        BigDecimal othersAmount = BigDecimal.ZERO;
        long othersCount = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalCount = 0L;

        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            long count = toLong(doc.get("count"));
            BigDecimal amount = toBigDecimal(doc.get("amount"));
            String label = labelOrUnknown(doc.get("label"));
            totalCount += count;
            totalAmount = totalAmount.add(amount);
            if (i < TOP_N) {
                labels.add(label);
                data.add(count);
            } else {
                othersCount += count;
                othersAmount = othersAmount.add(amount);
            }
        }
        if (othersCount > 0) {
            labels.add("Others");
            data.add(othersCount);
        }

        Map<String, Object> extras = baseExtras(query, range);
        extras.put("groupField", statusField);
        extras.put("amountField", amountField);
        extras.put("totalCount", totalCount);
        extras.put("totalAmount", totalAmount);
        if (othersCount > 0) {
            extras.put("othersCount", othersCount);
            extras.put("othersAmount", othersAmount);
        }

        return new AnalysisResponse("pie", labels,
                List.of(new AnalysisResponse.Dataset("Invoices", data)),
                cfg.getDisplayName() + " Status", extras);
    }

    private AnalysisResponse totals(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        String amountField = resolveField("totalAmount", aliases);
        LOG.infof("Invoice totals amount=%s", amountField);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(buildMatchStage(query, cfg, range, aliases));
        pipeline.add(new Document("$group", new Document("_id", null)
                .append("amount", new Document("$sum", new Document("$ifNull", List.of("$" + amountField, 0))))
                .append("count", new Document("$sum", 1))));

        List<Document> docs = runPipeline(cfg, pipeline);
        if (docs.isEmpty()) {
            return emptyResponse("kpi", "Amount", cfg.getDisplayName() + " Totals", range, query);
        }

        Document totals = docs.get(0);
        BigDecimal amount = toBigDecimal(totals.get("amount"));
        long count = toLong(totals.get("count"));

        List<String> labels = List.of(cfg.getDisplayName());
        List<Number> data = List.of(amount);

        Map<String, Object> extras = baseExtras(query, range);
        extras.put("totalAmount", amount);
        extras.put("totalCount", count);

        return new AnalysisResponse("kpi", labels,
                List.of(new AnalysisResponse.Dataset("Amount", data)),
                cfg.getDisplayName() + " Totals", extras);
    }
}
