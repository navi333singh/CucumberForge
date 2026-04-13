package com.cucumberforge.plugin.actions;


import com.cucumberforge.plugin.services.SettingsService;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.cucumberforge.plugin.util.JavaCodeGenerator;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Action that generates Testcontainers configuration based on project dependencies.
 */
public class GenerateTestcontainersAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        SettingsService settings = SettingsService.getInstance(project);
        String basePackage = settings.getBasePackage();
        if (basePackage.isEmpty()) basePackage = "com.example.test";

        // Detect database from project
        String dbType = detectDatabase(project);
        String image = getDockerImage(dbType);

        String code = JavaCodeGenerator.generateTestcontainersConfig(basePackage, dbType, image);
        String packagePath = (basePackage + ".config").replace('.', '/');

        String finalBasePackage = basePackage;
        WriteCommandAction.runWriteCommandAction(project, "Generate Testcontainers Config", null, () -> {
            try {
                VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
                if (baseDir == null) return;

                VirtualFile configDir = findOrCreateDir(baseDir, "src/test/java/" + packagePath);
                String fileName = "TestcontainersConfiguration.java";

                VirtualFile existing = configDir.findChild(fileName);
                if (existing != null) {
                    CucumberUtils.notify(project,
                            fileName + " already exists.",
                            NotificationType.WARNING);
                    return;
                }

                VirtualFile newFile = configDir.createChildData(this, fileName);
                newFile.setBinaryContent(code.getBytes(StandardCharsets.UTF_8));

                CucumberUtils.notify(project,
                        "Testcontainers configuration generated!",
                        NotificationType.INFORMATION);

            } catch (IOException ex) {
                CucumberUtils.notify(project,
                        "Error: " + ex.getMessage(),
                        NotificationType.ERROR);
            }
        });
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

    private String detectDatabase(Project project) {
        VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return "postgresql";

        // Check build files for database drivers
        String[] buildFiles = {"build.gradle", "build.gradle.kts", "pom.xml"};
        for (String fileName : buildFiles) {
            VirtualFile file = baseDir.findChild(fileName);
            if (file != null) {
                try {
                    String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                    if (content.contains("mysql")) return "mysql";
                    if (content.contains("mongodb") || content.contains("mongo")) return "mongodb";
                    if (content.contains("mariadb")) return "mariadb";
                    if (content.contains("postgresql") || content.contains("postgres")) return "postgresql";
                } catch (IOException ignored) {}
            }
        }

        // Check application.yml/properties
        VirtualFile resources = baseDir.findFileByRelativePath("src/main/resources");
        if (resources != null) {
            for (VirtualFile child : resources.getChildren()) {
                if (child.getName().startsWith("application")) {
                    try {
                        String content = new String(child.contentsToByteArray(), StandardCharsets.UTF_8);
                        if (content.contains("mysql")) return "mysql";
                        if (content.contains("mongodb")) return "mongodb";
                        if (content.contains("postgresql") || content.contains("postgres")) return "postgresql";
                    } catch (IOException ignored) {}
                }
            }
        }

        return "postgresql"; // Default
    }

    private String getDockerImage(String dbType) {
        switch (dbType.toLowerCase()) {
            case "mysql": return "mysql:8.0";
            case "mongodb": return "mongo:7.0";
            case "mariadb": return "mariadb:11";
            default: return "postgres:16-alpine";
        }
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
