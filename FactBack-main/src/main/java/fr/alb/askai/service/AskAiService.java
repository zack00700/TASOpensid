package fr.alb.askai.service;

import fr.alb.askai.dto.AnalysisResponse;
import fr.alb.askai.dto.UserQuery;
import fr.alb.askai.model.AskAiSpec;
import fr.alb.askai.model.ChartTypes;
import fr.alb.dto.ErrorResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.bson.Document;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.HashMap;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@ApplicationScoped
public class AskAiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();


    private static final Logger LOG = Logger.getLogger(AskAiService.class);
    private static final ZoneId DEFAULT_TZ = ZoneId.of("Europe/Paris");

    @Inject MongoService mongoService;
    @Inject TableDetectionService tableDetectionService;
    @Inject ClaudeIntentService claudeIntentService;

    @Inject @Named("invoiceAnalyzer") Analyzer invoiceAnalyzer;
    @Inject @Named("billOfLadingAnalyzer") Analyzer billOfLadingAnalyzer;

    @Inject @Named("invoiceConfig") TableConfig invoiceConfig;
    @Inject @Named("billOfLadingConfig") TableConfig billOfLadingConfig;

    public AskAiSpec handle(String question) {
        LOG.infof("Processing question: %s", question);

        try {
            // 1) Ask Claude to interpret the question (collection + analysisType + date filters)
            ClaudeIntent intent = claudeIntentService.detectIntent(question);

            String detectedTable;
            String analysisTypeHint;
            Map<String, Object> filters = new HashMap<>();

            if (intent != null) {
                detectedTable = intent.collection != null ? intent.collection : tableDetectionService.detectTable(question);
                analysisTypeHint = intent.analysisType;
                ZonedDateTime fromZdt = parseIntentDate(intent.from, false);
                ZonedDateTime toZdt = parseIntentDate(intent.to, true);
                if (fromZdt != null) filters.put("from", fromZdt);
                if (toZdt != null) filters.put("to", toZdt);
                LOG.infof("Claude intent: table=%s type=%s from=%s to=%s",
                        detectedTable, analysisTypeHint, fromZdt, toZdt);
            } else {
                // Fallback to keyword detection when Claude is unavailable
                detectedTable = tableDetectionService.detectTable(question);
                analysisTypeHint = null;
                LOG.infof("Keyword fallback: table=%s", detectedTable);
            }

            // 2) Pick config + analyzer
            TableConfig config = getTableConfig(detectedTable);
            Analyzer analyzer = getAnalyzer(detectedTable);

            // 3) Build query and run analysis (charts/KPIs)
            UserQuery userQuery = new UserQuery(question, filters, analysisTypeHint);
            AnalysisResponse analysis = analyzer.analyze(userQuery, config);

            // 3b) If Claude narrowed the range and it yielded nothing, retry with the config default range.
            boolean claudeProvidedDates = filters.containsKey("from") || filters.containsKey("to");
            if (claudeProvidedDates && isAnalysisEmpty(analysis)) {
                LOG.infof("Empty result with Claude-provided dates — retrying with default range");
                Map<String, Object> fallbackFilters = new HashMap<>(filters);
                fallbackFilters.remove("from");
                fallbackFilters.remove("to");
                UserQuery fallbackQuery = new UserQuery(question, fallbackFilters, analysisTypeHint);
                AnalysisResponse fallback = analyzer.analyze(fallbackQuery, config);
                if (!isAnalysisEmpty(fallback)) {
                    String note = " (No results in the requested period; showing the default range instead.)";
                    analysis = new AnalysisResponse(
                            fallback.chartType(),
                            fallback.labels(),
                            fallback.datasets(),
                            fallback.summary() + note,
                            fallback.extras());
                    userQuery = fallbackQuery;
                }
            }

            // 4) Convert to AskAiSpec (charts + populated table)
            AskAiSpec spec = convertToAskAiSpec(analysis, config, question, userQuery);

            LOG.infof("Analysis completed for table: %s", config.getCollectionName());
            return spec;
        } catch (CircuitBreakerOpenException e) {
            LOG.warn("OpenAI circuit breaker is open — service temporarily unavailable", e);
            throw new WebApplicationException(
                    Response.status(Response.Status.SERVICE_UNAVAILABLE)
                            .entity(new ErrorResponse("SERVICE_UNAVAILABLE", "The AI service is temporarily unavailable. Please try again later.", 503))
                            .build());
        } catch (TimeoutException e) {
            LOG.warn("OpenAI request timed out", e);
            throw new WebApplicationException(
                    Response.status(Response.Status.SERVICE_UNAVAILABLE)
                            .entity(new ErrorResponse("SERVICE_UNAVAILABLE", "The AI service did not respond in time. Please try again later.", 503))
                            .build());
        }
    }

    private TableConfig getTableConfig(String detectedTable) {
        return switch (detectedTable) {
            case "BILL_OF_LADING" -> billOfLadingConfig;
            case "INVOICE" -> invoiceConfig;
            default -> {
                LOG.warnf("Unknown table %s, defaulting to INVOICE", detectedTable);
                yield invoiceConfig;
            }
        };
    }

    private Analyzer getAnalyzer(String detectedTable) {
        return switch (detectedTable) {
            case "BILL_OF_LADING" -> billOfLadingAnalyzer;
            case "INVOICE" -> invoiceAnalyzer;
            default -> {
                LOG.warnf("Unknown table %s, defaulting to invoiceAnalyzer", detectedTable);
                yield invoiceAnalyzer;
            }
        };
    }

    private AskAiSpec convertToAskAiSpec(AnalysisResponse analysis,
                                         TableConfig config,
                                         String question,
                                         UserQuery userQuery) {
        AskAiSpec spec = new AskAiSpec();

        // --- Header/answer ---
        spec.setTitle(createTitle(question));
        spec.setAnswer(analysis.summary());
        spec.setNormalizedQuery(question);

        // --- Chart ---
        AskAiSpec.Chart chart = new AskAiSpec.Chart();
        ChartTypes type = resolveChartType(analysis.chartType());
        chart.setType(type);
        chart.setLabels(analysis.labels() != null ? analysis.labels() : new ArrayList<>());

        List<AskAiSpec.Dataset> datasets = new ArrayList<>();
        if (analysis.datasets() != null && !analysis.datasets().isEmpty()) {
            for (AnalysisResponse.Dataset ds : analysis.datasets()) {
                AskAiSpec.Dataset specDs = new AskAiSpec.Dataset(ds.label());
                List<Double> data = ds.data().stream()
                        .map(Number::doubleValue)
                        .collect(Collectors.toList());
                specDs.setData(data);
                datasets.add(specDs);
            }
        } else {
            AskAiSpec.Dataset defaultDs = new AskAiSpec.Dataset("Value");
            defaultDs.setData(List.of(0.0));
            datasets.add(defaultDs);
        }
        chart.setDatasets(datasets);
        spec.setChart(chart);

        // --- Table (columns + rows from Mongo) ---
        AskAiSpec.Table table = new AskAiSpec.Table();
        table.setColumns(new ArrayList<>(config.getDefaultColumns()));
        table.setRows(buildTableRows(userQuery, config)); // ← actually populate rows
        spec.setTable(table);

        // --- Aggregation (mirror the same pipeline we used for the table) ---
        List<org.bson.Document> pipelineDocs = buildPipeline(userQuery, config); // still Documents for Mongo
        AskAiSpec.Aggregation agg = new AskAiSpec.Aggregation();
        agg.setCollection(config.getCollectionName());
        agg.setPipeline(toJsonNodes(pipelineDocs));  // convert to JsonNode list for the spec
        spec.setAggregation(agg);

        return spec;
    }

    private ChartTypes resolveChartType(String chartTypeRaw) {
        if (chartTypeRaw == null) return ChartTypes.bar;
        String chartType = chartTypeRaw.trim().toLowerCase(Locale.ROOT);
        try {
            if ("kpi".equals(chartType)) return ChartTypes.kpi;
            return ChartTypes.valueOf(chartType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ChartTypes.bar; // safe fallback
        }
    }

    private List<JsonNode> toJsonNodes(List<org.bson.Document> docs) {
        List<JsonNode> out = new ArrayList<>(docs.size());
        for (org.bson.Document d : docs) {
            try {
                // safest: serialize to canonical JSON then parse
                out.add(MAPPER.readTree(d.toJson()));
            } catch (Exception e) {
                // fallback: direct tree from Map view
                out.add(MAPPER.valueToTree(d));
            }
        }
        return out;
    }


    private boolean isAnalysisEmpty(AnalysisResponse analysis) {
        if (analysis == null) return true;
        if (analysis.datasets() == null || analysis.datasets().isEmpty()) return true;
        return analysis.datasets().stream()
                .flatMap(ds -> ds.data() == null ? java.util.stream.Stream.<Number>empty() : ds.data().stream())
                .allMatch(n -> n == null || n.doubleValue() == 0d);
    }

    private ZonedDateTime parseIntentDate(String isoDate, boolean endOfDay) {
        if (isoDate == null || isoDate.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(isoDate.trim());
            return endOfDay
                    ? date.atTime(23, 59, 59).atZone(DEFAULT_TZ)
                    : date.atStartOfDay(DEFAULT_TZ);
        } catch (DateTimeParseException e) {
            LOG.warnf("Unable to parse Claude intent date '%s' — ignoring", isoDate);
            return null;
        }
    }

    private String createTitle(String question) {
        String title = question == null ? "" : question.trim();
        if (title.length() > 50) title = title.substring(0, 47) + "...";
        if (!title.isEmpty()) title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        return title;
    }

    /**
     * Build the MongoDB pipeline used both for the UI aggregation and for fetching table rows.
     */
    private List<Document> buildPipeline(UserQuery userQuery, TableConfig cfg) {
        Document match = buildMatch(userQuery, cfg);

        // $project only default columns (supporting dotted fields by renaming keys temporarily)
        Document project = new Document();
        for (String col : cfg.getDefaultColumns()) {
            project.append(col.replace(".", "__dot__"), "$" + col);
        }

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", match));
        pipeline.add(new Document("$project", project));
        pipeline.add(new Document("$sort", new Document(cfg.getDateField(), -1)));
        pipeline.add(new Document("$limit", 50));

        return pipeline;
    }

    /**
     * Build a $match document using default date range + any simple filters in the query.
     */
    private Document buildMatch(UserQuery userQuery, TableConfig cfg) {
        Map<String, String> aliases = cfg.getFieldAliases() != null ? cfg.getFieldAliases() : Map.of();

        ZonedDateTime from = cfg.getDefaultDateRange(DEFAULT_TZ).getFrom();
        ZonedDateTime to   = cfg.getDefaultDateRange(DEFAULT_TZ).getTo();

        if (userQuery != null && userQuery.filters() != null) {
            Object f = userQuery.filters().get("from");
            Object t = userQuery.filters().get("to");
            if (f instanceof ZonedDateTime z1) from = z1;
            if (t instanceof ZonedDateTime z2) to = z2;
        }

        Document match = new Document(cfg.getDateField(),
                new Document("$gte", Date.from(from.toInstant()))
                        .append("$lte", Date.from(to.toInstant())));

        if (userQuery != null && userQuery.filters() != null) {
            for (Map.Entry<String, Object> e : userQuery.filters().entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();
                if (v == null) continue;
                if ("from".equalsIgnoreCase(k) || "to".equalsIgnoreCase(k)) continue;
                String field = aliases.getOrDefault(k.toLowerCase(Locale.ROOT), k);
                match.append(field, v);
            }
        }
        return match;
    }

    /**
     * Execute the pipeline and map results into table rows as strings.
     */
    private List<List<String>> buildTableRows(UserQuery userQuery, TableConfig cfg) {
        List<Document> pipeline = buildPipeline(userQuery, cfg);
        List<Document> docs = mongoService.aggregateDocuments(cfg.getCollectionName(), pipeline);

        List<List<String>> rows = new ArrayList<>();
        for (Document d : docs) {
            List<String> row = new ArrayList<>();
            for (String col : cfg.getDefaultColumns()) {
                Object v = d.get(col.replace(".", "__dot__"));
                row.add(v == null ? "" : String.valueOf(v));
            }
            rows.add(row);
        }
        return rows;
    }
}
