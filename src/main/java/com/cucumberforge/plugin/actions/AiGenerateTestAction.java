package com.cucumberforge.plugin.actions;

import com.cucumberforge.plugin.services.AiService;
import com.cucumberforge.plugin.services.ProjectAnalyzerService;
import com.cucumberforge.plugin.services.StepDefinitionService;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Action that uses AI to generate Cucumber tests from a Java class.
 * Available on controllers, services, and any Java class.
 */
public class AiGenerateTestAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || !(psiFile instanceof PsiJavaFile)) return;

        PsiJavaFile javaFile = (PsiJavaFile) psiFile;

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "AI: Generating Cucumber Tests...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Analyzing class...");
                indicator.setFraction(0.1);

                ProjectAnalyzerService analyzer = ProjectAnalyzerService.getInstance(project);
                StepDefinitionService stepService = StepDefinitionService.getInstance(project);
                AiService aiService = AiService.getInstance(project);

                // Build context
                String classContext = analyzer.buildAiContext(javaFile);
                indicator.setFraction(0.3);

                indicator.setText("Loading existing tests...");
                String existingFeatures = stepService.getExistingFeaturesAsContext();
                String existingSteps = stepService.getExistingStepsAsContext();
                indicator.setFraction(0.4);

                indicator.setText("Generating tests with AI...");
                try {
                    AiService.GenerationResult result = aiService.generateTests(
                            classContext, existingFeatures, existingSteps).get();

                    if (!result.isSuccess()) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                CucumberUtils.notify(project, result.getError(), NotificationType.ERROR));
                        return;
                    }

                    indicator.setFraction(0.8);
                    indicator.setText("Writing generated files...");

                    ApplicationManager.getApplication().invokeLater(() ->
                        WriteCommandAction.runWriteCommandAction(project, "AI Generate Tests", null, () -> {
                            try {
                                VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
                                if (baseDir == null) return;

                                // Write feature file
                                String featureContent = result.extractFeatureContent();
                                if (!featureContent.isEmpty()) {
                                    VirtualFile featuresDir = findOrCreateDir(baseDir, "src/test/resources/features");
                                    String featureName = javaFile.getClasses()[0].getName();
                                    featureName = featureName != null ? toSnakeCase(featureName) : "generated";
                                    VirtualFile featureFile = featuresDir.createChildData(this,
                                            featureName + ".feature");
                                    featureFile.setBinaryContent(featureContent.getBytes(StandardCharsets.UTF_8));

                                    // Open the generated feature file
                                    FileEditorManager.getInstance(project).openFile(featureFile, true);
                                }

                                // Write step definitions
                                String javaContent = result.extractJavaContent();
                                if (!javaContent.isEmpty()) {
                                    // Extract package from generated code
                                    String pkg = extractPackage(javaContent);
                                    if (pkg.isEmpty()) {
                                        pkg = "com.example.test.steps";
                                    }
                                    String pkgPath = pkg.replace('.', '/');
                                    VirtualFile stepsDir = findOrCreateDir(baseDir, "src/test/java/" + pkgPath);

                                    String className = extractClassName(javaContent);
                                    if (className.isEmpty()) className = "GeneratedStepDefinitions";

                                    VirtualFile stepFile = stepsDir.createChildData(this, className + ".java");
                                    stepFile.setBinaryContent(javaContent.getBytes(StandardCharsets.UTF_8));
                                }

                                CucumberUtils.notify(project,
                                        "AI tests generated successfully!",
                                        NotificationType.INFORMATION);

                            } catch (IOException ex) {
                                CucumberUtils.notify(project,
                                        "Error writing files: " + ex.getMessage(),
                                        NotificationType.ERROR);
                            }
                        })
                    );

                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            CucumberUtils.notify(project,
                                    "AI generation failed: " + ex.getMessage(),
                                    NotificationType.ERROR));
                }
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        boolean isJavaFile = psiFile instanceof PsiJavaFile;
        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean isFeatureFile = vf != null && "feature".equals(vf.getExtension());
        e.getPresentation().setEnabledAndVisible(project != null && (isJavaFile || isFeatureFile));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    // --- Helpers ---

    private VirtualFile findOrCreateDir(VirtualFile parent, String path) throws IOException {
        String[] parts = path.split("/");
        VirtualFile current = parent;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            VirtualFile child = current.findChild(part);
            if (child == null) child = current.createChildDirectory(this, part);
            current = child;
        }
        return current;
    }

    private String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private String extractPackage(String javaCode) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^package\\s+([\\w.]+);")
                .matcher(javaCode);
        return m.find() ? m.group(1) : "";
    }

    private String extractClassName(String javaCode) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("public\\s+class\\s+(\\w+)")
                .matcher(javaCode);
        return m.find() ? m.group(1) : "";
    }
}
