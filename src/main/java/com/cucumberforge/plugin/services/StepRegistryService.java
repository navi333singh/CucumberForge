package com.cucumberforge.plugin.services;

import com.cucumberforge.plugin.model.StepDefinitionModel;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Registry that indexes all Cucumber step definitions across the project.
 * Provides lookup, duplicate detection, and usage analysis.
 */
@Service(Service.Level.PROJECT)
public final class StepRegistryService {

    private static final Pattern METHOD_PATTERN =
            Pattern.compile("@(Given|When|Then|And|But)\\(\"(.+?)\"\\)\\s+public\\s+void\\s+(\\w+)\\(([^)]*)\\)");

    private final Project project;
    private List<StepDefinitionModel> cachedDefinitions;
    private long lastRefreshTime = 0;
    private static final long CACHE_TTL_MS = 5000; // 5 seconds

    public StepRegistryService(@NotNull Project project) {
        this.project = project;
    }

    public static StepRegistryService getInstance(@NotNull Project project) {
        return project.getService(StepRegistryService.class);
    }

    /**
     * Get all step definitions in the project (with caching).
     */
    public List<StepDefinitionModel> getAllStepDefinitions() {
        if (cachedDefinitions != null && System.currentTimeMillis() - lastRefreshTime < CACHE_TTL_MS) {
            return cachedDefinitions;
        }
        refresh();
        return cachedDefinitions;
    }

    /**
     * Force refresh the step definition index.
     */
    public void refresh() {
        cachedDefinitions = ReadAction.compute(() -> {
            List<StepDefinitionModel> definitions = new ArrayList<>();
            List<VirtualFile> stepFiles = CucumberUtils.findStepDefinitionFiles(project);

            for (VirtualFile file : stepFiles) {
                PsiFile psi = PsiManager.getInstance(project).findFile(file);
                if (psi != null) {
                    definitions.addAll(parseStepDefinitions(psi.getText(), file.getPath(), file.getNameWithoutExtension()));
                }
            }
            return definitions;
        });
        lastRefreshTime = System.currentTimeMillis();
    }

    /**
     * Find step definitions matching a given step text.
     */
    public List<StepDefinitionModel> findMatchingDefinitions(String stepText) {
        return getAllStepDefinitions().stream()
                .filter(def -> matchesStep(def.getPattern(), stepText))
                .collect(Collectors.toList());
    }

    /**
     * Get duplicate step patterns (patterns that appear more than once).
     */
    public Map<String, List<StepDefinitionModel>> getDuplicates() {
        return getAllStepDefinitions().stream()
                .collect(Collectors.groupingBy(StepDefinitionModel::getPattern))
                .entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Get all unique step patterns.
     */
    public Set<String> getAllPatterns() {
        return getAllStepDefinitions().stream()
                .map(StepDefinitionModel::getPattern)
                .collect(Collectors.toSet());
    }

    /**
     * Get step definition count.
     */
    public int getStepCount() {
        return getAllStepDefinitions().size();
    }

    /**
     * Get step definitions grouped by file.
     */
    public Map<String, List<StepDefinitionModel>> getDefinitionsByFile() {
        return getAllStepDefinitions().stream()
                .collect(Collectors.groupingBy(StepDefinitionModel::getFilePath));
    }

    // --- Private helpers ---

    private List<StepDefinitionModel> parseStepDefinitions(String content, String filePath, String className) {
        List<StepDefinitionModel> defs = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            Matcher annotationMatcher = Pattern.compile("@(Given|When|Then|And|But)\\(\"(.+?)\"\\)").matcher(line);

            if (annotationMatcher.find()) {
                String annotation = annotationMatcher.group(1);
                String pattern = annotationMatcher.group(2);

                // Find the method name on the same or next line
                String methodName = "unknown";
                String params = "";
                String searchArea = line;
                if (i + 1 < lines.length) searchArea += " " + lines[i + 1].trim();

                Matcher methodMatcher = Pattern.compile("public\\s+void\\s+(\\w+)\\(([^)]*)\\)").matcher(searchArea);
                if (methodMatcher.find()) {
                    methodName = methodMatcher.group(1);
                    params = methodMatcher.group(2);
                }

                List<StepDefinitionModel.StepParameter> parameters = parseParameters(params);

                defs.add(new StepDefinitionModel(
                        "@" + annotation, pattern, methodName, parameters,
                        className, filePath, i + 1, true
                ));
            }
        }
        return defs;
    }

    private List<StepDefinitionModel.StepParameter> parseParameters(String params) {
        List<StepDefinitionModel.StepParameter> result = new ArrayList<>();
        if (params == null || params.trim().isEmpty()) return result;

        String[] parts = params.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            String[] typeAndName = trimmed.split("\\s+");
            if (typeAndName.length >= 2) {
                result.add(new StepDefinitionModel.StepParameter(
                        typeAndName[typeAndName.length - 1],
                        typeAndName[typeAndName.length - 2],
                        ""
                ));
            }
        }
        return result;
    }

    private boolean matchesStep(String pattern, String stepText) {
        try {
            String regex = pattern
                    .replace("{string}", "\"[^\"]*\"")
                    .replace("{int}", "\\d+")
                    .replace("{float}", "\\d+\\.\\d+")
                    .replace("{word}", "\\S+")
                    .replace("{}", ".+");
            // Anchor the pattern if it's not already a regex
            if (!regex.startsWith("^")) {
                regex = "^" + regex + "$";
            }
            return stepText.matches(regex);
        } catch (Exception e) {
            return pattern.equals(stepText);
        }
    }
}
