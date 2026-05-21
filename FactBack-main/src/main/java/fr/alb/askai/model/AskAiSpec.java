package fr.alb.askai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AskAiSpec {

    private String title;
    private String answer;
    private String notes;
    private String normalizedQuery;
    @NotNull
    private Aggregation aggregation;
    private Chart chart;
    private Table table;

    // getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getNormalizedQuery() { return normalizedQuery; }
    public void setNormalizedQuery(String normalizedQuery) { this.normalizedQuery = normalizedQuery; }
    public Aggregation getAggregation() { return aggregation; }
    public void setAggregation(Aggregation aggregation) { this.aggregation = aggregation; }
    public Chart getChart() { return chart; }
    public void setChart(Chart chart) { this.chart = chart; }
    public Table getTable() { return table; }
    public void setTable(Table table) { this.table = table; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Aggregation {
        @NotBlank
        @Pattern(regexp = "invoices|customers|payments")
        private String collection;
        @NotNull
        private List<JsonNode> pipeline = new ArrayList<>();

        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
        public List<JsonNode> getPipeline() { return pipeline; }
        public void setPipeline(List<JsonNode> pipeline) { this.pipeline = pipeline; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chart {
        private ChartTypes type;
        private List<String> labels = new ArrayList<>();
        private List<Dataset> datasets = new ArrayList<>();
        private String yUnit;
        private String xFormat;
        private List<String> suggestedColors = new ArrayList<>();

        public ChartTypes getType() { return type; }
        public void setType(ChartTypes type) { this.type = type; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
        public List<Dataset> getDatasets() { return datasets; }
        public void setDatasets(List<Dataset> datasets) { this.datasets = datasets; }
        public String getYUnit() { return yUnit; }
        public void setYUnit(String yUnit) { this.yUnit = yUnit; }
        public String getXFormat() { return xFormat; }
        public void setXFormat(String xFormat) { this.xFormat = xFormat; }
        public List<String> getSuggestedColors() { return suggestedColors; }
        public void setSuggestedColors(List<String> suggestedColors) { this.suggestedColors = suggestedColors; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dataset {
        private String name;
        private List<Double> data = new ArrayList<>();

        public Dataset() {}
        public Dataset(String name) { this.name = name; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<Double> getData() { return data; }
        public void setData(List<Double> data) { this.data = data; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Table {
        private List<String> columns = new ArrayList<>();
        private List<List<String>> rows = new ArrayList<>();

        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        public List<List<String>> getRows() { return rows; }
        public void setRows(List<List<String>> rows) { this.rows = rows; }
    }
}
