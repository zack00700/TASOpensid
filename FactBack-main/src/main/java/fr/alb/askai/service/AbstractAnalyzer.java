package fr.alb.askai.service;

import fr.alb.askai.dto.AnalysisResponse;
import fr.alb.askai.dto.UserQuery;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

public abstract class AbstractAnalyzer implements Analyzer {

    protected static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Paris");
    private static final Logger LOG = Logger.getLogger(AbstractAnalyzer.class);

    @Inject
    protected MongoService mongoService;

    protected DateRange resolveDateRange(UserQuery query, TableConfig cfg) {
        DateRange defaults = cfg.getDefaultDateRange(DEFAULT_ZONE);
        ZonedDateTime from = defaults.getFrom();
        ZonedDateTime to = defaults.getTo();

        Map<String, Object> filters = query != null ? query.filters() : null;
        if (filters != null) {
            ZonedDateTime requestedFrom = parseDate(filters.get("from"));
            ZonedDateTime requestedTo = parseDate(filters.get("to"));
            if (requestedFrom != null) {
                from = requestedFrom;
            }
            if (requestedTo != null) {
                to = requestedTo;
            }
        }

        try {
            DateRange range = new DateRange(from, to);
            LOG.debugf("Resolved date range: %s -> %s", range.getFrom(), range.getTo());
            return range;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid date range: " + e.getMessage());
        }
    }

    private ZonedDateTime parseDate(Object raw) {
        if (raw == null) return null;
        if (raw instanceof ZonedDateTime zdt) return zdt;
        if (raw instanceof OffsetDateTime odt) return odt.atZoneSameInstant(DEFAULT_ZONE);
        if (raw instanceof LocalDateTime ldt) return ldt.atZone(DEFAULT_ZONE);
        if (raw instanceof LocalDate ld) return ld.atStartOfDay(DEFAULT_ZONE);
        if (raw instanceof Instant instant) return instant.atZone(DEFAULT_ZONE);
        if (raw instanceof Date date) return date.toInstant().atZone(DEFAULT_ZONE);
        if (raw instanceof Number number) {
            long epochMillis = number.longValue();
            return Instant.ofEpochMilli(epochMillis).atZone(DEFAULT_ZONE);
        }
        if (raw instanceof String text) {
            String value = text.trim();
            if (value.isEmpty()) return null;
            try {
                return ZonedDateTime.parse(value);
            } catch (DateTimeParseException e) {
                try {
                    return OffsetDateTime.parse(value).atZoneSameInstant(DEFAULT_ZONE);
                } catch (DateTimeParseException ignored) {
                    try {
                        return LocalDateTime.parse(value).atZone(DEFAULT_ZONE);
                    } catch (DateTimeParseException e1) {
                        try {
                            return LocalDate.parse(value).atStartOfDay(DEFAULT_ZONE);
                        } catch (DateTimeParseException e2) {
                            try {
                                return Instant.parse(value).atZone(DEFAULT_ZONE);
                            } catch (DateTimeParseException e3) {
                                LOG.debugf("Unable to parse date value '%s'", value);
                                return null;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    protected Document buildMatchStage(UserQuery query, TableConfig cfg, DateRange range, Map<String, String> aliases) {
        Document criteria = new Document();
        Document dateBounds = new Document("$gte", Date.from(range.getFrom().toInstant()))
                .append("$lte", Date.from(range.getTo().toInstant()));
        criteria.append(cfg.getDateField(), dateBounds);

        Map<String, Object> filters = query != null ? query.filters() : null;
        if (filters != null) {
            filters.forEach((key, value) -> {
                if (value == null || isDateKey(key)) return;
                String field = resolveField(key, aliases);
                criteria.append(field, value);
            });
        }

        return new Document("$match", criteria);
    }

    protected boolean isDateKey(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("from") || normalized.equals("to");
    }

    protected String resolveField(String key, Map<String, String> aliases) {
        if (key == null) return null;
        if (aliases == null || aliases.isEmpty()) return key;
        String direct = aliases.get(key);
        if (direct != null) return direct;
        direct = aliases.get(key.toLowerCase(Locale.ROOT));
        if (direct != null) return direct;
        return key;
    }

    protected Map<String, Object> baseExtras(UserQuery query, DateRange range) {
        Map<String, Object> extras = new LinkedHashMap<>();
        Map<String, String> rangeMap = new LinkedHashMap<>();
        rangeMap.put("from", range.getFrom().toString());
        rangeMap.put("to", range.getTo().toString());
        extras.put("range", rangeMap);

        Map<String, Object> additionalFilters = extractAdditionalFilters(query);
        if (!additionalFilters.isEmpty()) {
            extras.put("filters", additionalFilters);
        }
        return extras;
    }

    private Map<String, Object> extractAdditionalFilters(UserQuery query) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (query == null || query.filters() == null) return extra;
        query.filters().forEach((key, value) -> {
            if (value == null || isDateKey(key)) return;
            extra.put(key, value);
        });
        return extra;
    }

    protected AnalysisResponse emptyResponse(String chartType, String datasetLabel, String ignoredSummary,
                                             DateRange range, UserQuery query) {
        String summary = "No results for the selected period/filters.";
        List<String> labels = List.of();
        List<AnalysisResponse.Dataset> datasets = datasetLabel == null
                ? List.of()
                : List.of(new AnalysisResponse.Dataset(datasetLabel, List.of()));
        Map<String, Object> extras = baseExtras(query, range);
        return new AnalysisResponse(chartType, labels, datasets, summary, extras);
    }

    protected List<Document> runPipeline(TableConfig cfg, List<Document> pipeline) {
        return mongoService.aggregateDocuments(cfg.getCollectionName(), pipeline);
    }

    protected ZonedDateTime monthStart(ZonedDateTime dateTime) {
        return dateTime.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    }

    protected ZonedDateTime inclusiveMonthlyEnd(DateRange range) {
        ZonedDateTime to = range.getTo();
        ZonedDateTime from = range.getFrom();
        if (to.isAfter(from)) {
            boolean isStartOfMonth = to.getDayOfMonth() == 1 && to.toLocalTime().equals(LocalTime.MIDNIGHT);
            if (isStartOfMonth) {
                ZonedDateTime previous = to.minusMonths(1);
                if (!previous.isBefore(from)) {
                    to = previous;
                }
            }
        }
        return monthStart(to);
    }

    protected long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Decimal128 decimal128) return decimal128.longValue();
        if (value instanceof Number number) return number.longValue();
        return 0L;
    }

    protected BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Decimal128 decimal128) return decimal128.bigDecimalValue();
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return BigDecimal.ZERO;
    }

    protected String labelOrUnknown(Object label) {
        if (label == null) return "Unknown";
        if (label instanceof String str) {
            String trimmed = str.trim();
            return trimmed.isEmpty() ? "Unknown" : trimmed;
        }
        return label.toString();
    }
}
