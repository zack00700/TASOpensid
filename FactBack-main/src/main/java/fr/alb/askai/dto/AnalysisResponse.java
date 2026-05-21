package fr.alb.askai.dto;

import java.util.List;
import java.util.Map;

public record AnalysisResponse(
        String chartType,
        List<String> labels,
        List<Dataset> datasets,
        String summary,
        Map<String, Object> extras
) {
    public record Dataset(String label, List<Number> data) {}
}
