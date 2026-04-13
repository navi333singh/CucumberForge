package com.cucumberforge.plugin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a parsed Gherkin feature file.
 */
public class FeatureFileModel {

    private final String filePath;
    private final String featureName;
    private final String description;
    private final List<String> tags;
    private final List<ScenarioModel> scenarios;
    private final BackgroundModel background;

    public FeatureFileModel(String filePath, String featureName, String description,
                            List<String> tags, List<ScenarioModel> scenarios, BackgroundModel background) {
        this.filePath = filePath;
        this.featureName = featureName;
        this.description = description != null ? description : "";
        this.tags = tags != null ? Collections.unmodifiableList(tags) : Collections.emptyList();
        this.scenarios = scenarios != null ? Collections.unmodifiableList(scenarios) : Collections.emptyList();
        this.background = background;
    }

    public String getFilePath() { return filePath; }
    public String getFeatureName() { return featureName; }
    public String getDescription() { return description; }
    public List<String> getTags() { return tags; }
    public List<ScenarioModel> getScenarios() { return scenarios; }
    public BackgroundModel getBackground() { return background; }

    // --- Inner classes ---

    public static class ScenarioModel {
        private final String name;
        private final List<String> tags;
        private final List<StepModel> steps;
        private final List<ExampleTable> examples;
        private final boolean outline;

        public ScenarioModel(String name, List<String> tags, List<StepModel> steps,
                             List<ExampleTable> examples, boolean outline) {
            this.name = name;
            this.tags = tags != null ? Collections.unmodifiableList(tags) : Collections.emptyList();
            this.steps = steps != null ? Collections.unmodifiableList(steps) : Collections.emptyList();
            this.examples = examples;
            this.outline = outline;
        }

        public String getName() { return name; }
        public List<String> getTags() { return tags; }
        public List<StepModel> getSteps() { return steps; }
        public List<ExampleTable> getExamples() { return examples; }
        public boolean isOutline() { return outline; }
    }

    public static class BackgroundModel {
        private final List<StepModel> steps;

        public BackgroundModel(List<StepModel> steps) {
            this.steps = steps != null ? Collections.unmodifiableList(steps) : Collections.emptyList();
        }

        public List<StepModel> getSteps() { return steps; }
    }

    public static class StepModel {
        private final StepKeyword keyword;
        private final String text;
        private final List<List<String>> dataTable;
        private final String docString;
        private final int lineNumber;

        public StepModel(StepKeyword keyword, String text, List<List<String>> dataTable,
                         String docString, int lineNumber) {
            this.keyword = keyword;
            this.text = text;
            this.dataTable = dataTable;
            this.docString = docString;
            this.lineNumber = lineNumber;
        }

        public StepKeyword getKeyword() { return keyword; }
        public String getText() { return text; }
        public List<List<String>> getDataTable() { return dataTable; }
        public String getDocString() { return docString; }
        public int getLineNumber() { return lineNumber; }
    }

    public enum StepKeyword {
        GIVEN("Given"),
        WHEN("When"),
        THEN("Then"),
        AND("And"),
        BUT("But");

        private final String display;

        StepKeyword(String display) {
            this.display = display;
        }

        public String getDisplay() { return display; }

        public static StepKeyword fromString(String text) {
            for (StepKeyword kw : values()) {
                if (kw.display.equalsIgnoreCase(text.trim())) {
                    return kw;
                }
            }
            return GIVEN;
        }
    }

    public static class ExampleTable {
        private final String name;
        private final List<String> headers;
        private final List<List<String>> rows;

        public ExampleTable(String name, List<String> headers, List<List<String>> rows) {
            this.name = name != null ? name : "";
            this.headers = headers != null ? Collections.unmodifiableList(headers) : Collections.emptyList();
            this.rows = rows != null ? Collections.unmodifiableList(rows) : Collections.emptyList();
        }

        public String getName() { return name; }
        public List<String> getHeaders() { return headers; }
        public List<List<String>> getRows() { return rows; }
    }
}
