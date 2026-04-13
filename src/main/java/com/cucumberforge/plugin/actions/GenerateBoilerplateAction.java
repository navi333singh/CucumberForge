package com.cucumberforge.plugin.actions;

import com.cucumberforge.plugin.model.ProjectConfig;
import com.cucumberforge.plugin.services.BoilerplateService;
import com.cucumberforge.plugin.ui.BoilerplateWizardDialog;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Action that launches the Cucumber boilerplate generation wizard.
 */
public class GenerateBoilerplateAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        BoilerplateWizardDialog dialog = new BoilerplateWizardDialog(project);
        if (dialog.showAndGet()) {
            ProjectConfig config = dialog.getConfig();
            BoilerplateService.getInstance(project).generateBoilerplate(config);
            CucumberUtils.notify(project,
                    "Cucumber boilerplate generated successfully!",
                    NotificationType.INFORMATION);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
