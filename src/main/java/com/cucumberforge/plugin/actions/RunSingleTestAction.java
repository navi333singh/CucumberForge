package com.cucumberforge.plugin.actions;

import com.cucumberforge.plugin.services.MavenRunConfigService;
import com.cucumberforge.plugin.services.SettingsService;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Action that allows running a single Cucumber test:
 * - A single feature file
 * - A single scenario by name
 * - A single test class
 * - Tests filtered by a Cucumber tag
 *
 * Creates a temporary run configuration or saves it for reuse.
 */
public class RunSingleTestAction extends AnAction {

    private static final Pattern SCENARIO_PATTERN = Pattern.compile(
            "^\\s*(?:Scenario|Scenario Outline):\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern TAG_PATTERN = Pattern.compile("@(\\w+)");

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (file == null) return;

        List<RunOption> options = new ArrayList<>();

        if ("feature".equals(file.getExtension())) {
            // Feature file context
            options.add(new RunOption("Run entire feature: " + file.getName(),
                    buildFeatureRunCommand(file)));

            // Extract scenarios from the file
            if (psiFile != null) {
                String content = psiFile.getText();
                Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(content);
                while (scenarioMatcher.find()) {
                    String scenarioName = scenarioMatcher.group(1).trim();
                    options.add(new RunOption("Run scenario: " + scenarioName,
                            MavenRunConfigService.buildScenarioTestCommand(scenarioName)));
                }

                // Extract tags
                Matcher tagMatcher = TAG_PATTERN.matcher(content);
                java.util.Set<String> tags = new java.util.LinkedHashSet<>();
                while (tagMatcher.find()) {
                    tags.add("@" + tagMatcher.group(1));
                }
                for (String tag : tags) {
                    options.add(new RunOption("Run tag: " + tag,
                            MavenRunConfigService.buildTagTestCommand(tag)));
                }
            }
        } else if (psiFile instanceof PsiJavaFile) {
            // Java test file context
            PsiJavaFile javaFile = (PsiJavaFile) psiFile;
            if (javaFile.getClasses().length > 0) {
                String className = javaFile.getClasses()[0].getQualifiedName();
                if (className != null) {
                    options.add(new RunOption("Run test class: " + javaFile.getClasses()[0].getName(),
                            MavenRunConfigService.buildSingleTestCommand(className)));
                }
            }
        }

        // Always offer custom tag run
        options.add(new RunOption("Run by custom tag...", null));

        if (options.size() == 1 && options.get(0).command == null) {
            // Only custom tag option — show input dialog
            promptAndRunByTag(project);
            return;
        }

        // Show popup menu
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(
                new BaseListPopupStep<RunOption>("Run Cucumber Test", options) {
                    @Override
                    public @NotNull String getTextFor(RunOption option) {
                        return option.label;
                    }

                    @Override
                    public PopupStep<?> onChosen(RunOption selectedValue, boolean finalChoice) {
                        return doFinalStep(() -> {
                            if (selectedValue.command == null) {
                                SwingUtilities.invokeLater(() -> promptAndRunByTag(project));
                            } else {
                                executeCommand(project, selectedValue.label, selectedValue.command);
                            }
                        });
                    }
                }
        );

        if (editor != null) {
            popup.showInBestPositionFor(editor);
        } else {
            popup.showInFocusCenter();
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean visible = project != null && file != null && (
                "feature".equals(file.getExtension()) ||
                e.getData(CommonDataKeys.PSI_FILE) instanceof PsiJavaFile
        );
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    // =================== Helpers ===================

    private void promptAndRunByTag(Project project) {
        String tag = Messages.showInputDialog(project,
                "Enter the Cucumber tag to run (e.g., @smoke):",
                "Run by Tag",
                null);
        if (tag != null && !tag.trim().isEmpty()) {
            if (!tag.startsWith("@")) tag = "@" + tag;
            executeCommand(project, "Run tag: " + tag,
                    MavenRunConfigService.buildTagTestCommand(tag));
        }
    }

    private void executeCommand(Project project, String name, String mavenCommand) {
        // Try to find Maven run configuration type
        RunManager runManager = RunManager.getInstance(project);

        try {
            // Look for Maven configuration type
            var mavenConfigType = findMavenConfigType();

            if (mavenConfigType != null) {
                // Create IntelliJ run configuration
                var factory = mavenConfigType.getConfigurationFactories()[0];
                RunnerAndConfigurationSettings settings = runManager.createConfiguration(name, factory);

                // Set Maven goals via reflection (the Maven plugin API varies by version)
                setMavenGoals(settings.getConfiguration(), mavenCommand);

                runManager.addConfiguration(settings);
                runManager.setSelectedConfiguration(settings);

                // Execute
                ExecutionManager.getInstance(project).restartRunProfile(
                        project,
                        DefaultRunExecutor.getRunExecutorInstance(),
                        null,
                        settings,
                        null
                );
                return;
            }
        } catch (Exception ex) {
            // Fall back to terminal command
        }

        // Fallback: show the command for manual execution
        String fullCommand = "mvn " + mavenCommand;
        CucumberUtils.notify(project,
                "Run this command in your terminal:\n" + fullCommand,
                NotificationType.INFORMATION);

        // Also save as a run config for next time
        com.cucumberforge.plugin.model.MavenRunConfig config =
                new com.cucumberforge.plugin.model.MavenRunConfig(
                        name, mavenCommand, "Single test run",
                        com.cucumberforge.plugin.model.MavenRunConfig.RunConfigType.TEST_SINGLE);

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project,
                "Save Run Config", null, () -> {
                    MavenRunConfigService.getInstance(project).saveAsIntelliJRunConfig(config);
                });

        CucumberUtils.notify(project,
                "Run configuration '" + name + "' saved to .idea/runConfigurations. " +
                "Reload IntelliJ configurations to see it.",
                NotificationType.INFORMATION);
    }

    private String buildFeatureRunCommand(VirtualFile featureFile) {
        String featurePath = featureFile.getPath();
        // Use cucumber.features to point to specific feature
        return "test -Dcucumber.features=\"" + featurePath + "\"";
    }

    /**
     * Attempt to find the Maven run configuration type via reflection.
     */
    private com.intellij.execution.configurations.ConfigurationType findMavenConfigType() {
        try {
            return com.intellij.execution.configurations.ConfigurationTypeUtil
                    .findConfigurationType("MavenRunConfiguration");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attempt to set Maven goals via reflection to avoid compile-time dependency on Maven plugin.
     */
    private void setMavenGoals(Object configuration, String goals) {
        try {
            var method = configuration.getClass().getMethod("getRunnerParameters");
            var params = method.invoke(configuration);
            if (params != null) {
                var setGoals = params.getClass().getMethod("setGoals", List.class);
                List<String> goalsList = List.of(goals.split("\\s+"));
                setGoals.invoke(params, goalsList);
            }
        } catch (Exception ignored) {
            // Maven plugin API not available
        }
    }

    // =================== Data class ===================

    private static class RunOption {
        final String label;
        final String command;

        RunOption(String label, String command) {
            this.label = label;
            this.command = command;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
