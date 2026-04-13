package com.cucumberforge.plugin.actions;

import com.cucumberforge.plugin.services.ProjectAnalyzerService;
import com.cucumberforge.plugin.services.SettingsService;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.cucumberforge.plugin.util.JavaCodeGenerator;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Action that generates a Test Data Builder class for a Java entity/POJO.
 */
public class GenerateTestDataBuilderAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || !(psiFile instanceof PsiJavaFile)) return;

        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        PsiClass[] classes = javaFile.getClasses();
        if (classes.length == 0) return;

        PsiClass mainClass = classes[0];
        String entityName = mainClass.getName();
        if (entityName == null) return;

        ProjectAnalyzerService analyzer = ProjectAnalyzerService.getInstance(project);
        Map<String, String> fields = analyzer.extractFields(mainClass);

        if (fields.isEmpty()) {
            CucumberUtils.notify(project, "No fields found in " + entityName, NotificationType.WARNING);
            return;
        }

        SettingsService settings = SettingsService.getInstance(project);
        String basePackage = settings.getBasePackage();
        if (basePackage.isEmpty()) basePackage = "com.example.test";

        String code = JavaCodeGenerator.generateTestDataBuilder(basePackage, entityName, fields);
        String packagePath = (basePackage + ".support").replace('.', '/');

        WriteCommandAction.runWriteCommandAction(project, "Generate Test Data Builder", null, () -> {
            try {
                VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
                if (baseDir == null) return;

                VirtualFile supportDir = findOrCreateDir(baseDir, "src/test/java/" + packagePath);
                String fileName = entityName + "Builder.java";

                VirtualFile existing = supportDir.findChild(fileName);
                if (existing != null) {
                    CucumberUtils.notify(project,
                            fileName + " already exists. Delete it first to regenerate.",
                            NotificationType.WARNING);
                    return;
                }

                VirtualFile newFile = supportDir.createChildData(this, fileName);
                newFile.setBinaryContent(code.getBytes(StandardCharsets.UTF_8));

                CucumberUtils.notify(project,
                        "Test Data Builder generated: " + fileName,
                        NotificationType.INFORMATION);

            } catch (IOException ex) {
                CucumberUtils.notify(project,
                        "Error generating builder: " + ex.getMessage(),
                        NotificationType.ERROR);
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(project != null && psiFile instanceof PsiJavaFile);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

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
}
