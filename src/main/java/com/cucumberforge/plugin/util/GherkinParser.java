package com.cucumberforge.plugin.util;

import com.cucumberforge.plugin.model.FeatureFileModel;
import com.cucumberforge.plugin.model.FeatureFileModel.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses raw Gherkin (.feature) text into a structured {@link FeatureFileModel}.
 */
public final class GherkinParser {

    private static final Pattern TAG_PATTERN = Pattern.compile("@\\S+");
    private static final Pattern FEATURE_PATTERN = Pattern.compile("^\\s*Feature:\\s*(.+)$");
    private static final Pattern SCENARIO_PATTERN = Pattern.compile("^\\s*Scenario:\\s*(.+)$");
    private static final Pattern OUTLINE_PATTERN = Pattern.compile("^\\s*Scenario Outline:\\s*(.+)$");
    private static final Pattern BACKGROUND_PATTERN = Pattern.compile("^\\s*Background:");
    private static final Pattern STEP_PATTERN = Pattern.compile("^\\s*(Given|When|Then|And|But)\\s+(.+)$");
    private static final Pattern EXAMPLES_PATTERN = Pattern.compile("^\\s*Examples:");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\s*\\|(.+)\\|\\s*$");
    private static final Pattern DOC_STRING_PATTERN = Pattern.compile("^\\s*\"\"\"");

    private GherkinParser() {}

    /**
     * Parse raw feature file content into a model.
     */
    public static FeatureFileModel parse(String filePath, String content) {
        String[] lines = content.split("\\r?\\n");
        String featureName = "";
        String description = "";
        List<String> featureTags = new ArrayList<>();
        List<ScenarioModel> scenarios = new ArrayList<>();
        BackgroundModel background = null;

        int i = 0;

        // Parse tags before Feature:
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.startsWith("@")) {
                featureTags.addAll(extractTags(line));
                i++;
            } else if (line.isEmpty() || line.startsWith("#")) {
                i++;
            } else {
                break;
            }
        }

        // Parse Feature:
        if (i < lines.length) {
            Matcher m = FEATURE_PATTERN.matcher(lines[i]);
            if (m.matches()) {
                featureName = m.group(1).trim();
                i++;
            }
        }

        // Parse description (lines between Feature and first Scenario/Background)
        StringBuilder desc = new StringBuilder();
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.isEmpty() || (!SCENARIO_PATTERN.matcher(line).matches()
                    && !OUTLINE_PATTERN.matcher(line).matches()
                    && !BACKGROUND_PATTERN.matcher(line).matches()
                    && !line.startsWith("@"))) {
                if (!line.isEmpty() && !line.startsWith("#")) {
                    desc.append(line).append("\n");
                }
                i++;
            } else {
                break;
            }
        }
        description = desc.toString().trim();

        // Parse body: Background, Scenarios
        while (i < lines.length) {
            String line = lines[i].trim();

            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }

            // Tags before scenario
            List<String> scenarioTags = new ArrayList<>();
            while (i < lines.length && lines[i].trim().startsWith("@")) {
                scenarioTags.addAll(extractTags(lines[i].trim()));
                i++;
            }
            if (i >= lines.length) break;
            line = lines[i].trim();

            // Background
            if (BACKGROUND_PATTERN.matcher(line).matches()) {
                i++;
                ParseResult<List<StepModel>> stepResult = parseSteps(lines, i);
                background = new BackgroundModel(stepResult.value);
                i = stepResult.nextIndex;
                continue;
            }

            // Scenario or Scenario Outline
            Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(line);
            Matcher outlineMatcher = OUTLINE_PATTERN.matcher(line);
            boolean isOutline = false;
            String scenarioName = "";

            if (outlineMatcher.matches()) {
                isOutline = true;
                scenarioName = outlineMatcher.group(1).trim();
                i++;
            } else if (scenarioMatcher.matches()) {
                scenarioName = scenarioMatcher.group(1).trim();
                i++;
            } else {
                i++;
                continue;
            }

            ParseResult<List<StepModel>> stepResult = parseSteps(lines, i);
            i = stepResult.nextIndex;

            List<ExampleTable> examples = null;
            if (isOutline) {
                examples = new ArrayList<>();
                while (i < lines.length && EXAMPLES_PATTERN.matcher(lines[i].trim()).matches()) {
                    i++;
                    ParseResult<ExampleTable> exResult = parseExampleTable(lines, i);
                    examples.add(exResult.value);
                    i = exResult.nextIndex;
                }
            }

            scenarios.add(new ScenarioModel(scenarioName, scenarioTags, stepResult.value, examples, isOutline));
        }

        return new FeatureFileModel(filePath, featureName, description, featureTags, scenarios, background);
    }

    /**
     * Extract all steps from a feature file text (without full model parsing).
     */
    public static List<StepModel> extractSteps(String content) {
        List<StepModel> steps = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher m = STEP_PATTERN.matcher(lines[i]);
            if (m.matches()) {
                StepKeyword keyword = StepKeyword.fromString(m.group(1));
                String text = m.group(2).trim();
                steps.add(new StepModel(keyword, text, null, null, i + 1));
            }
        }
        return steps;
    }

    /**
     * Extract unique step texts from content.
     */
    public static List<String> extractUniqueStepTexts(String content) {
        return extractSteps(content).stream()
                .map(s -> s.getKeyword().getDisplay() + " " + s.getText())
                .distinct()
                .collect(Collectors.toList());
    }

    // --- Private helpers ---

    private static ParseResult<List<StepModel>> parseSteps(String[] lines, int start) {
        List<StepModel> steps = new ArrayList<>();
        int i = start;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }
            Matcher stepMatcher = STEP_PATTERN.matcher(line);
            if (stepMatcher.matches()) {
                StepKeyword keyword = StepKeyword.fromString(stepMatcher.group(1));
                String text = stepMatcher.group(2).trim();
                i++;

                // Check for DataTable or DocString
                List<List<String>> dataTable = null;
                String docString = null;

                if (i < lines.length) {
                    if (TABLE_ROW_PATTERN.matcher(lines[i].trim()).matches()) {
                        ParseResult<List<List<String>>> tableResult = parseDataTable(lines, i);
                        dataTable = tableResult.value;
                        i = tableResult.nextIndex;
                    } else if (DOC_STRING_PATTERN.matcher(lines[i].trim()).matches()) {
                        ParseResult<String> docResult = parseDocString(lines, i);
                        docString = docResult.value;
                        i = docResult.nextIndex;
                    }
                }

                steps.add(new StepModel(keyword, text, dataTable, docString, start));
            } else {
                break;
            }
        }
        return new ParseResult<>(steps, i);
    }

    private static ParseResult<List<List<String>>> parseDataTable(String[] lines, int start) {
        List<List<String>> table = new ArrayList<>();
        int i = start;
        while (i < lines.length) {
            Matcher m = TABLE_ROW_PATTERN.matcher(lines[i].trim());
            if (m.matches()) {
                String[] cells = m.group(1).split("\\|");
                List<String> row = Arrays.stream(cells)
                        .map(String::trim)
                        .collect(Collectors.toList());
                table.add(row);
                i++;
            } else {
                break;
            }
        }
        return new ParseResult<>(table, i);
    }

    private static ParseResult<String> parseDocString(String[] lines, int start) {
        StringBuilder sb = new StringBuilder();
        int i = start + 1; // skip opening """
        while (i < lines.length) {
            if (DOC_STRING_PATTERN.matcher(lines[i].trim()).matches()) {
                i++;
                break;
            }
            sb.append(lines[i]).append("\n");
            i++;
        }
        return new ParseResult<>(sb.toString().trim(), i);
    }

    private static ParseResult<ExampleTable> parseExampleTable(String[] lines, int start) {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        int i = start;
        boolean headerParsed = false;

        while (i < lines.length) {
            Matcher m = TABLE_ROW_PATTERN.matcher(lines[i].trim());
            if (m.matches()) {
                String[] cells = m.group(1).split("\\|");
                List<String> row = Arrays.stream(cells)
                        .map(String::trim)
                        .collect(Collectors.toList());
                if (!headerParsed) {
                    headers = row;
                    headerParsed = true;
                } else {
                    rows.add(row);
                }
                i++;
            } else if (lines[i].trim().isEmpty()) {
                i++;
            } else {
                break;
            }
        }
        return new ParseResult<>(new ExampleTable("", headers, rows), i);
    }

    private static List<String> extractTags(String line) {
        List<String> tags = new ArrayList<>();
        Matcher m = TAG_PATTERN.matcher(line);
        while (m.find()) {
            tags.add(m.group());
        }
        return tags;
    }

    private static class ParseResult<T> {
        final T value;
        final int nextIndex;
        ParseResult(T value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }
}
