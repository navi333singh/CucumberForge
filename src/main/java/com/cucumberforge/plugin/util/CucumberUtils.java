package com.cucumberforge.plugin.util;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for Cucumber / BDD operations within the IntelliJ project.
 */
public final class CucumberUtils {

    private static final Pattern CUCUMBER_ANNOTATION_PATTERN =
            Pattern.compile("@(Given|When|Then|And|But)\\s*\\(\\s*\"(.+?)\"\\s*\\)");

    private CucumberUtils() {}

    /**
     * Find all .feature files in the project.
     */
    public static List<VirtualFile> findFeatureFiles(Project project) {
        List<VirtualFile> featureFiles = new ArrayList<>();
        Collection<VirtualFile> files = com.intellij.psi.search.FilenameIndex.getAllFilesByExt(
                project, "feature", GlobalSearchScope.projectScope(project)
        );
        featureFiles.addAll(files);
        return featureFiles;
    }

    /**
     * Find all Java files that contain Cucumber step annotations.
     */
    public static List<VirtualFile> findStepDefinitionFiles(Project project) {
        List<VirtualFile> stepFiles = new ArrayList<>();
        FileType javaType = FileTypeManager.getInstance().getFileTypeByExtension("java");
        Collection<VirtualFile> allJava = FileTypeIndex.getFiles(
                javaType, GlobalSearchScope.projectScope(project));

        for (VirtualFile file : allJava) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile instanceof PsiJavaFile) {
                String text = psiFile.getText();
                if (text.contains("@Given") || text.contains("@When") || text.contains("@Then")
                        || text.contains("@And") || text.contains("@But")) {
                    stepFiles.add(file);
                }
            }
        }
        return stepFiles;
    }

    /**
     * Extract Cucumber step patterns from a Java file's text.
     */
    public static Map<String, String> extractStepPatterns(String javaContent) {
        Map<String, String> patterns = new LinkedHashMap<>();
        Matcher m = CUCUMBER_ANNOTATION_PATTERN.matcher(javaContent);
        while (m.find()) {
            String annotation = m.group(1);
            String pattern = m.group(2);
            patterns.put(pattern, annotation);
        }
        return patterns;
    }

    /**
     * Detect the test source root for the project.
     * Returns the path to src/test/java or a reasonable default.
     */
    public static String detectTestSourceRoot(Project project) {
        VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return "src/test/java";

        VirtualFile testJava = baseDir.findFileByRelativePath("src/test/java");
        if (testJava != null && testJava.isDirectory()) {
            return testJava.getPath();
        }
        return baseDir.getPath() + "/src/test/java";
    }

    /**
     * Detect features resource root (src/test/resources/features).
     */
    public static String detectFeaturesRoot(Project project) {
        VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return "src/test/resources/features";

        VirtualFile features = baseDir.findFileByRelativePath("src/test/resources/features");
        if (features != null && features.isDirectory()) {
            return features.getPath();
        }
        return baseDir.getPath() + "/src/test/resources/features";
    }

    /**
     * Detect if the project uses Gradle or Maven.
     */
    public static boolean isGradleProject(Project project) {
        VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return false;
        return baseDir.findChild("build.gradle") != null
                || baseDir.findChild("build.gradle.kts") != null;
    }

    /**
     * Detect if the project uses Spring Boot.
     */
    public static boolean isSpringBootProject(Project project) {
        VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return false;

        // Check Gradle
        VirtualFile buildGradle = baseDir.findChild("build.gradle");
        if (buildGradle == null) buildGradle = baseDir.findChild("build.gradle.kts");
        if (buildGradle != null) {
            PsiFile psi = PsiManager.getInstance(project).findFile(buildGradle);
            if (psi != null && psi.getText().contains("spring-boot")) {
                return true;
            }
        }

        // Check Maven
        VirtualFile pom = baseDir.findChild("pom.xml");
        if (pom != null) {
            PsiFile psi = PsiManager.getInstance(project).findFile(pom);
            if (psi != null && psi.getText().contains("spring-boot")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert a feature name to a valid Java class name.
     */
    public static String toClassName(String featureName) {
        String[] words = featureName.replaceAll("[^a-zA-Z0-9\\s]", "").split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
            }
        }
        String result = sb.toString();
        if (result.isEmpty()) return "StepDefinitions";
        if (!result.endsWith("StepDefinitions") && !result.endsWith("Steps")) {
            result += "StepDefinitions";
        }
        return result;
    }

    /**
     * Show a balloon notification via the CucumberForge notification group.
     */
    public static void notify(Project project, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("CucumberForge")
                .createNotification(content, type)
                .notify(project);
    }
}
