package com.cucumberforge.plugin.model;

import java.util.Collections;
import java.util.List;

/**
 * Represents a step definition method in Java source code.
 */
public class StepDefinitionModel {

    private final String annotation;      // @Given, @When, @Then
    private final String pattern;         // Cucumber expression or regex
    private final String methodName;      // Java method name
    private final List<StepParameter> parameters;
    private final String className;
    private final String filePath;
    private final int lineNumber;
    private final boolean hasImplementation;

    public StepDefinitionModel(String annotation, String pattern, String methodName,
                               List<StepParameter> parameters, String className,
                               String filePath, int lineNumber, boolean hasImplementation) {
        this.annotation = annotation;
        this.pattern = pattern;
        this.methodName = methodName;
        this.parameters = parameters != null ? Collections.unmodifiableList(parameters) : Collections.emptyList();
        this.className = className != null ? className : "";
        this.filePath = filePath != null ? filePath : "";
        this.lineNumber = lineNumber;
        this.hasImplementation = hasImplementation;
    }

    public String getAnnotation() { return annotation; }
    public String getPattern() { return pattern; }
    public String getMethodName() { return methodName; }
    public List<StepParameter> getParameters() { return parameters; }
    public String getClassName() { return className; }
    public String getFilePath() { return filePath; }
    public int getLineNumber() { return lineNumber; }
    public boolean isHasImplementation() { return hasImplementation; }

    // --- Inner classes ---

    public static class StepParameter {
        private final String name;
        private final String type;           // String, int, double, DataTable, etc.
        private final String cucumberType;   // {string}, {int}, {float}, etc.

        public StepParameter(String name, String type, String cucumberType) {
            this.name = name;
            this.type = type;
            this.cucumberType = cucumberType != null ? cucumberType : "";
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getCucumberType() { return cucumberType; }
    }

    public enum StepStatus {
        IMPLEMENTED,
        UNDEFINED,
        AMBIGUOUS,
        DUPLICATE
    }

    public static class StepMapping {
        private final FeatureFileModel.StepModel step;
        private final StepDefinitionModel definition;
        private final StepStatus status;

        public StepMapping(FeatureFileModel.StepModel step, StepDefinitionModel definition, StepStatus status) {
            this.step = step;
            this.definition = definition;
            this.status = status;
        }

        public FeatureFileModel.StepModel getStep() { return step; }
        public StepDefinitionModel getDefinition() { return definition; }
        public StepStatus getStatus() { return status; }
    }
}
