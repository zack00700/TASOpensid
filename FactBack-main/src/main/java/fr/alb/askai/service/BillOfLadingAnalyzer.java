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

@ApplicationScoped
@Named("billOfLadingAnalyzer")
public class BillOfLadingAnalyzer extends AbstractAnalyzer {

    private static final Logger LOG = Logger.getLogger(BillOfLadingAnalyzer.class);
    private static final int TOP_N = 12;

    private enum AnalysisType {
        PORT_OF_DISCHARGE,
        PORT_OF_LOADING,
        CUSTOMER,
        MONTHLY_TREND,
        TOTALS,
        STATUS,
        HAZARDOUS,
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
            throw new BadRequestException("Unsupported question for Bill of Lading analysis: " + query.question());
        }

        Map<String, String> aliases = cfg.getFieldAliases();
        DateRange range = resolveDateRange(query, cfg);
        LOG.infof("BillOfLading analysis=%s range=%s -> %s", type, range.getFrom(), range.getTo());

        return switch (type) {
            case PORT_OF_DISCHARGE -> groupByField("pod", "bar", "BLs",
                    cfg.getDisplayName() + " by Port of Discharge", query, cfg, range, aliases);
            case PORT_OF_LOADING -> groupByField("pol", "bar", "BLs",
                    cfg.getDisplayName() + " by Port of Loading", query, cfg, range, aliases);
            case CUSTOMER -> groupByCustomer(query, cfg, range, aliases);
            case MONTHLY_TREND -> monthlyTrend(query, cfg, range, aliases);
            case TOTALS -> totals(query, cfg, range, aliases);
            case STATUS -> statusDistribution(query, cfg, range, aliases);
            case HAZARDOUS -> hazardousAnalysis(query, cfg, range, aliases);
            case COUNT -> simpleCount(query, cfg, range, aliases); // ✅ NOUVEAU
        };
    }

    private AnalysisType resolveAnalysisType(UserQuery query) {
        if (query.analysisTypeHint() != null) {
            try {
                return AnalysisType.valueOf(query.analysisTypeHint());
            } catch (IllegalArgumentException ignored) {
                LOG.debugf("Unknown analysisTypeHint '%s' for BillOfLading, falling back to keyword detection",
                        query.analysisTypeHint());
            }
        }
        return detectAnalysis(query.question());
    }

    private AnalysisType detectAnalysis(String question) {
        String q = question.toLowerCase(Locale.ROOT);

        // Hazardous questions (plus spécifique en premier)
        if (containsAny(q, "hazardous", "dangerous", "hazard", "dangerous goods", "dangerous cargo")) {
            return AnalysisType.HAZARDOUS;
        }

        // ✅ NOUVEAU : Simple counting (avant TOTALS)
        if (isSimpleCountQuestion(q)) {
            return AnalysisType.COUNT;
        }

        // Status questions
        if (containsAny(q, "status", "stage", "progress")) {
            return AnalysisType.STATUS;
        }

        // Trends
        if (containsAny(q, "trend", "over time", "monthly", "per month", "timeline")) {
            return AnalysisType.MONTHLY_TREND;
        }

        // Ports
        if (containsAny(q, "port of discharge", "pod", "destination", "discharge")) {
            return AnalysisType.PORT_OF_DISCHARGE;
        }
        if (containsAny(q, "port of loading", "pol", "origin", "loading")) {
            return AnalysisType.PORT_OF_LOADING;
        }

        // Customers
        if (containsAny(q, "customer", "consignee", "shipper", "client")) {
            return AnalysisType.CUSTOMER;
        }

        // ✅ MODIFIÉ : TOTALS pour agrégations spécifiques seulement
        if (containsAny(q, "total weight", "total volume", "total packages", "overall weight", "overall volume", "total cargo",
                "weight", "volume", "packages")) {
            return AnalysisType.TOTALS;
        }

        return null;
    }

    // ✅ NOUVELLE MÉTHODE : Détection des questions de comptage simple
    private boolean isSimpleCountQuestion(String q) {
        // Questions qui demandent juste "combien" sans spécifier weight/volume/packages
        return (containsAny(q, "how many", "count of", "number of") &&
                containsAny(q, "bl", "bills", "shipment", "lading")) ||
                (containsAny(q, "total") &&
                        containsAny(q, "bl", "bills", "shipment", "lading") &&
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

    // ✅ NOUVELLE MÉTHODE : Comptage simple
    private AnalysisResponse simpleCount(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        LOG.infof("BillOfLading simple count analysis");

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(buildMatchStage(query, cfg, range, aliases));
        pipeline.add(new Document("$count", "total"));

        List<Document> docs = runPipeline(cfg, pipeline);
        long count = docs.isEmpty() ? 0L : toLong(docs.get(0).get("total"));

        // Format KPI
        List<String> labels = List.of("Total BLs");
        List<Number> data = List.of(count);

        Map<String, Object> extras = baseExtras(query, range);
        extras.put("totalCount", count);

        String summary = String.format("Found %d %s", count,
                count == 1 ? cfg.getDisplayName() : cfg.getDisplayName() + "s");

        return new AnalysisResponse("kpi", labels,
                List.of(new AnalysisResponse.Dataset("Count", data)),
                summary, extras);
    }

    // ✅ NOUVELLE MÉTHODE : Analyse des marchandises dangereuses
    private AnalysisResponse hazardousAnalysis(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        String hazardousField = resolveField("hazardous", aliases);
        LOG.infof("BillOfLading hazardous analysis using field %s", hazardousField);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(buildMatchStage(query, cfg, range, aliases));

        // Groupement par statut hazardous (true/false/null)
        pipeline.add(new Document("$group", new Document("_id",
                new Document("$ifNull", List.of("$" + hazardousField, false)))
                .append("count", new Document("$sum", 1))));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("label", new Document("$cond", List.of(
                        new Document("$eq", List.of("$_id", true)),
                        "Hazardous",
                        "Non-Hazardous"
                )))
                .append("count", "$count")));
        pipeline.add(new Document("$sort", new Document("count", -1)));

        List<Document> docs = runPipeline(cfg, pipeline);
        if (docs.isEmpty()) {
            return emptyResponse("pie", "BLs", "No hazardous cargo data", range, query);
        }

        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        long total = 0L;
        long hazardousCount = 0L;

        for (Document doc : docs) {
            String label = labelOrUnknown(doc.get("label"));
            long count = toLong(doc.get("count"));
            labels.add(label);
            data.add(count);
            total += count;
            if ("Hazardous".equals(label)) {
                hazardousCount = count;
            }
        }

        Map<String, Object> extras = baseExtras(query, range);
        extras.put("groupField", hazardousField);
        extras.put("totalCount", total);
        extras.put("hazardousCount", hazardousCount);
        extras.put("hazardousPercentage", total > 0 ? (hazardousCount * 100.0 / total) : 0);

        String summary = String.format("Found %d hazardous out of %d total BLs (%.1f%%)",
                hazardousCount, total, total > 0 ? (hazardousCount * 100.0 / total) : 0);

        return new AnalysisResponse("pie", labels,
                List.of(new AnalysisResponse.Dataset("BLs", data)),
                summary, extras);
    }

    private AnalysisResponse groupByField(String aliasKey, String chartType, String datasetLabel, String summary,
                                          UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        String field = resolveField(aliasKey, aliases);
        LOG.infof("BillOfLading grouping by %s (alias %s)", field, aliasKey);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(buildMatchStage(query, cfg, range, aliases));

        Document groupExpr;
        if (field.contains(".") || "hazardous".equals(aliasKey)) {
            groupExpr = new Document("$ifNull", List.of("$" + field, "Unknown"));
        } else {
            groupExpr = new Document("$ifNull", List.of("$" + field, "Unknown"));
        }

        pipeline.add(new Document("$group", new Document("_id", groupExpr)
                .append("count", new Document("$sum", 1))));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("label", "$_id")
                .append("count", "$count")));
        pipeline.add(new Document("$sort", new Document("count", -1)));

        List<Document> docs = runPipeline(cfg, pipeline);
        if (docs.isEmpty()) {
            return emptyResponse(chartType, datasetLabel, summary, range, query);
        }

        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        long others = 0L;
        long total = 0L;
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            long count = toLong(doc.get("count"));
            String label = labelOrUnknown(doc.get("label"));
            total += count;
            if (i < TOP_N) {
                labels.add(label);
                data.add(count);
            } else {
                others += count;
            }
        }
        if (others > 0) {
            labels.add("Others");
            data.add(others);
        }

        Map<String, Object> extras = baseExtras(query, range);
        extras.put("groupField", field);
        extras.put("totalCount", total);
        if (others > 0) {
            extras.put("othersCount", others);
        }

        return new AnalysisResponse(chartType, labels,
                List.of(new AnalysisResponse.Dataset(datasetLabel, data)),
                summary, extras);
    }

    private AnalysisResponse groupByCustomer(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        String question = query.question().toLowerCase(Locale.ROOT);
        String aliasKey;
        if (question.contains("shipper")) {
            aliasKey = "shipperName";
        } else if (question.contains("notify")) {
            aliasKey = "notify";
        } else if (question.contains("consignee")) {
            aliasKey = "consigneeName";
        } else {
            aliasKey = "customer";
        }
        String resolvedField = resolveField(aliasKey, aliases);
        if (resolvedField.equals(aliasKey) && !"consignee".equals(aliasKey)) {
            aliasKey = "consigneeName";
            resolvedField = resolveField(aliasKey, aliases);
        }
        LOG.infof("BillOfLading customer grouping using alias %s resolved field %s", aliasKey, resolvedField);

        return groupByField(aliasKey, "bar", "BLs",
                cfg.getDisplayName() + " by Customer", query, cfg, range, aliases);
    }

    private AnalysisResponse monthlyTrend(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        String dateField = cfg.getDateField();
        LOG.infof("BillOfLading monthly trend on %s", dateField);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(buildMatchStage(query, cfg, range, aliases));
        Document dateToString = new Document("$dateToString", new Document("format", "%Y-%m")
                .append("date", "$" + dateField)
                .append("timezone", DEFAULT_ZONE.getId()));
        pipeline.add(new Document("$group", new Document("_id", dateToString)
                .append("count", new Document("$sum", 1))));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("label", "$_id")
                .append("count", "$count")));
        pipeline.add(new Document("$sort", new Document("label", 1)));

        List<Document> docs = runPipeline(cfg, pipeline);
        if (docs.isEmpty()) {
            return emptyResponse("line", "BLs", cfg.getDisplayName() + " Monthly Trend", range, query);
        }

        Map<String, Long> values = new LinkedHashMap<>();
        long total = 0L;
        for (Document doc : docs) {
            String label = labelOrUnknown(doc.get("label"));
            long count = toLong(doc.get("count"));
            values.put(label, count);
            total += count;
        }

        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        ZonedDateTime cursor = monthStart(range.getFrom());
        ZonedDateTime end = inclusiveMonthlyEnd(range);
        while (!cursor.isAfter(end)) {
            String label = String.format("%d-%02d", cursor.getYear(), cursor.getMonthValue());
            labels.add(label);
            data.add(values.getOrDefault(label, 0L));
            cursor = cursor.plusMonths(1);
        }

        Map<String, Object> extras = baseExtras(query, range);
        extras.put("dateField", dateField);
        extras.put("totalCount", total);

        return new AnalysisResponse("line", labels,
                List.of(new AnalysisResponse.Dataset("BLs", data)),
                cfg.getDisplayName() + " Monthly Trend", extras);
    }

    private AnalysisResponse totals(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        String weightField = resolveField("weight", aliases);
        String volumeField = resolveField("volume", aliases);
        String packagesField = resolveField("packages", aliases);
        LOG.infof("BillOfLading totals weight=%s volume=%s packages=%s", weightField, volumeField, packagesField);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(buildMatchStage(query, cfg, range, aliases));
        pipeline.add(new Document("$group", new Document("_id", null)
                .append("weight", new Document("$sum", new Document("$ifNull", List.of("$" + weightField, 0))))
                .append("volume", new Document("$sum", new Document("$ifNull", List.of("$" + volumeField, 0))))
                .append("packages", new Document("$sum", new Document("$ifNull", List.of("$" + packagesField, 0))))
                .append("count", new Document("$sum", 1))));

        List<Document> docs = runPipeline(cfg, pipeline);
        if (docs.isEmpty()) {
            return emptyResponse("bar", "Totals", cfg.getDisplayName() + " Totals", range, query);
        }

        Document totals = docs.get(0);
        BigDecimal weight = toBigDecimal(totals.get("weight"));
        BigDecimal volume = toBigDecimal(totals.get("volume"));
        long packages = toLong(totals.get("packages"));
        long count = toLong(totals.get("count"));

        List<String> labels = List.of("Weight (kg)", "Volume (m³)", "Packages");
        List<Number> data = List.of(weight, volume, packages);

        Map<String, Object> extras = baseExtras(query, range);
        Map<String, Object> totalsMap = new LinkedHashMap<>();
        totalsMap.put("weightKg", weight);
        totalsMap.put("volumeM3", volume);
        totalsMap.put("packages", packages);
        totalsMap.put("records", count);
        extras.put("totals", totalsMap);

        return new AnalysisResponse("bar", labels,
                List.of(new AnalysisResponse.Dataset("Totals", data)),
                cfg.getDisplayName() + " Totals", extras);
    }

    private AnalysisResponse statusDistribution(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        String field = resolveField("status", aliases);
        LOG.infof("BillOfLading status distribution using field %s", field);

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(buildMatchStage(query, cfg, range, aliases));
        pipeline.add(new Document("$group", new Document("_id", new Document("$ifNull", List.of("$" + field, "Unknown")))
                .append("count", new Document("$sum", 1))));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("label", "$_id")
                .append("count", "$count")));
        pipeline.add(new Document("$sort", new Document("count", -1)));

        List<Document> docs = runPipeline(cfg, pipeline);
        if (docs.isEmpty()) {
            return emptyResponse("pie", "BLs", cfg.getDisplayName() + " Status", range, query);
        }

        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        long others = 0L;
        long total = 0L;
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            long count = toLong(doc.get("count"));
            String label = labelOrUnknown(doc.get("label"));
            total += count;
            if (i < TOP_N) {
                labels.add(label);
                data.add(count);
            } else {
                others += count;
            }
        }
        if (others > 0) {
            labels.add("Others");
            data.add(others);
        }

        Map<String, Object> extras = baseExtras(query, range);
        extras.put("groupField", field);
        extras.put("totalCount", total);
        if (others > 0) {
            extras.put("othersCount", others);
        }

        return new AnalysisResponse("pie", labels,
                List.of(new AnalysisResponse.Dataset("BLs", data)),
                cfg.getDisplayName() + " Status", extras);
    }
}