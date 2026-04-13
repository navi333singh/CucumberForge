package com.cucumberforge.plugin.actions;

import com.cucumberforge.plugin.services.StepDefinitionService;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Action that generates Java step definitions from a .feature file.
 * Available in editor context menu when a .feature file is open.
 */
public class GenerateStepDefsAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || file == null) return;

        int count = StepDefinitionService.getInstance(project).generateStepDefinitions(file);

        if (count > 0) {
            CucumberUtils.notify(project,
                    "Generated " + count + " step definition(s) from " + file.getName(),
                    NotificationType.INFORMATION);
        } else {
            CucumberUtils.notify(project,
                    "No undefined steps found in " + file.getName(),
                    NotificationType.INFORMATION);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean isFeatureFile = file != null && "feature".equals(file.getExtension());
        e.getPresentation().setEnabledAndVisible(project != null && isFeatureFile);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
