package com.cucumberforge.plugin.actions;

import com.cucumberforge.plugin.templates.ScenarioTemplateManager;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Action that inserts predefined BDD scenario templates into .feature files.
 */
public class InsertScenarioTemplateAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) return;

        List<ScenarioTemplateManager.ScenarioTemplate> templates = ScenarioTemplateManager.getTemplates();

        JBPopupFactory.getInstance()
                .createListPopup(new BaseListPopupStep<ScenarioTemplateManager.ScenarioTemplate>(
                        "Insert Scenario Template", templates) {

                    @NotNull
                    @Override
                    public String getTextFor(ScenarioTemplateManager.ScenarioTemplate value) {
                        return value.getName() + " — " + value.getDescription();
                    }

                    @Override
                    public PopupStep<?> onChosen(ScenarioTemplateManager.ScenarioTemplate selectedValue, boolean finalChoice) {
                        if (finalChoice) {
                            insertTemplate(project, editor, selectedValue);
                        }
                        return FINAL_CHOICE;
                    }
                })
                .showInBestPositionFor(editor);
    }

    private void insertTemplate(Project project, Editor editor, ScenarioTemplateManager.ScenarioTemplate template) {
        WriteCommandAction.runWriteCommandAction(project, "Insert Scenario Template", null, () -> {
            int offset = editor.getCaretModel().getOffset();
            String content = "\n" + template.getContent() + "\n";
            editor.getDocument().insertString(offset, content);
            editor.getCaretModel().moveToOffset(offset + content.length());
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean isFeatureFile = file != null && "feature".equals(file.getExtension());
        e.getPresentation().setEnabledAndVisible(project != null && editor != null && isFeatureFile);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
