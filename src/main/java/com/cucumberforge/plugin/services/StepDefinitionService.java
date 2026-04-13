package com.cucumberforge.plugin.services;

import com.cucumberforge.plugin.model.FeatureFileModel.StepModel;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.cucumberforge.plugin.util.GherkinParser;
import com.cucumberforge.plugin.util.JavaCodeGenerator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that generates step definition Java files from .feature files.
 */
@Service(Service.Level.PROJECT)
public final class StepDefinitionService {

    private final Project project;

    public StepDefinitionService(@NotNull Project project) {
        this.project = project;
    }

    public static StepDefinitionService getInstance(@NotNull Project project) {
        return project.getService(StepDefinitionService.class);
    }

    /**
     * Generate step definitions for a .feature file.
     * Returns the number of steps generated.
     */
    public int generateStepDefinitions(VirtualFile featureFile) {
        String content = ReadAction.compute(() -> {
            PsiFile psi = PsiManager.getInstance(project).findFile(featureFile);
            return psi != null ? psi.getText() : null;
        });

        if (content == null || content.isEmpty()) return 0;

        // Parse steps from the feature file
        List<StepModel> allSteps = GherkinParser.extractSteps(content);
        if (allSteps.isEmpty()) return 0;

        // Get existing step patterns in the project
        Set<String> existingPatterns = getExistingStepPatterns();

        // Filter out already-implemented steps
        List<StepModel> undefinedSteps = allSteps.stream()
                .filter(step -> !isStepDefined(step, existingPatterns))
                .collect(Collectors.toList());

        if (undefinedSteps.isEmpty()) return 0;

        // Determine output location
        SettingsService settings = SettingsService.getInstance(project);
        String basePackage = settings.getBasePackage();
        if (basePackage.isEmpty()) {
            basePackage = detectBaseTestPackage();
        }

        // Generate class name from feature name
        String featureName = featureFile.getNameWithoutExtension();
        String className = CucumberUtils.toClassName(featureName);
        String stepsPackage = basePackage + ".steps";

        // Generate the Java code
        String code = JavaCodeGenerator.generateStepDefinitionClass(stepsPackage, className, undefinedSteps);

        // Write the file
        String packagePath = stepsPackage.replace('.', '/');
        String finalBasePackage = basePackage;
        WriteCommandAction.runWriteCommandAction(project, "Generate Step Definitions", null, () -> {
            try {
                VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
                if (baseDir == null) return;

                VirtualFile testJava = findOrCreateDir(baseDir, "src/test/java/" + packagePath);
                VirtualFile existing = testJava.findChild(className + ".java");
                if (existing != null) {
                    // Append methods to existing file
                    appendToExistingFile(existing, undefinedSteps);
                } else {
                    VirtualFile newFile = testJava.createChildData(this, className + ".java");
                    newFile.setBinaryContent(code.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to create step definitions: " + e.getMessage(), e);
            }
        });

        return undefinedSteps.size();
    }

    /**
     * Get all existing step definition patterns in the project.
     */
    public Set<String> getExistingStepPatterns() {
        return ReadAction.compute(() -> {
            Set<String> patterns = new HashSet<>();
            List<VirtualFile> stepFiles = CucumberUtils.findStepDefinitionFiles(project);
            for (VirtualFile file : stepFiles) {
                PsiFile psi = PsiManager.getInstance(project).findFile(file);
                if (psi != null) {
                    Map<String, String> filePatterns = CucumberUtils.extractStepPatterns(psi.getText());
                    patterns.addAll(filePatterns.keySet());
                }
            }
            return patterns;
        });
    }

    /**
     * Get all existing step definitions as formatted text (for AI context).
     */
    public String getExistingStepsAsContext() {
        return ReadAction.compute(() -> {
            StringBuilder sb = new StringBuilder();
            List<VirtualFile> stepFiles = CucumberUtils.findStepDefinitionFiles(project);
            for (VirtualFile file : stepFiles) {
                PsiFile psi = PsiManager.getInstance(project).findFile(file);
                if (psi != null) {
                    sb.append("// File: ").append(file.getName()).append("\n");
                    sb.append(psi.getText()).append("\n\n");
                }
            }
            return sb.toString();
        });
    }

    /**
     * Get all existing feature files content (for AI context).
     */
    public String getExistingFeaturesAsContext() {
        return ReadAction.compute(() -> {
            StringBuilder sb = new StringBuilder();
            List<VirtualFile> featureFiles = CucumberUtils.findFeatureFiles(project);
            for (VirtualFile file : featureFiles) {
                PsiFile psi = PsiManager.getInstance(project).findFile(file);
                if (psi != null) {
                    sb.append("# File: ").append(file.getName()).append("\n");
                    sb.append(psi.getText()).append("\n\n");
                }
            }
            return sb.toString();
        });
    }

    // --- Private helpers ---

    private boolean isStepDefined(StepModel step, Set<String> existingPatterns) {
        String stepText = step.getText();
        for (String pattern : existingPatterns) {
            // Simple matching: check if the pattern could match the step text
            String regex = cucumberExpressionToRegex(pattern);
            if (stepText.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    private String cucumberExpressionToRegex(String expression) {
        String regex = expression
                .replace("{string}", "\"[^\"]*\"")
                .replace("{int}", "\\d+")
                .replace("{float}", "\\d+\\.\\d+")
                .replace("{word}", "\\S+")
                .replace("{}", ".+");
        // Also handle regex patterns (starting with ^)
        if (expression.startsWith("^")) {
            return expression;
        }
        return "^" + regex + "$";
    }

    private String detectBaseTestPackage() {
        VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return "com.example.test";

        VirtualFile testJava = baseDir.findFileByRelativePath("src/test/java");
        if (testJava != null && testJava.isDirectory()) {
            // Walk down single-child directories to find the base package
            VirtualFile current = testJava;
            StringBuilder pkg = new StringBuilder();
            while (current.getChildren().length == 1 && current.getChildren()[0].isDirectory()) {
                current = current.getChildren()[0];
                if (pkg.length() > 0) pkg.append(".");
                pkg.append(current.getName());
            }
            if (pkg.length() > 0) return pkg.toString();
        }
        return "com.example.test";
    }

    private void appendToExistingFile(VirtualFile file, List<StepModel> steps) throws IOException {
        String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
        int lastBrace = content.lastIndexOf('}');
        if (lastBrace < 0) return;

        StringBuilder newMethods = new StringBuilder();
        Set<String> usedNames = new HashSet<>();
        for (StepModel step : steps) {
            newMethods.append("\n").append(JavaCodeGenerator.generateStepMethod(step, usedNames));
        }

        String newContent = content.substring(0, lastBrace) + newMethods + "\n" + content.substring(lastBrace);
        file.setBinaryContent(newContent.getBytes(StandardCharsets.UTF_8));
    }

    private VirtualFile findOrCreateDir(VirtualFile parent, String path) throws IOException {
        String[] parts = path.split("/");
        VirtualFile current = parent;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            VirtualFile child = current.findChild(part);
            if (child == null) {
                child = current.createChildDirectory(this, part);
            }
            current = child;
        }
        return current;
    }
}
