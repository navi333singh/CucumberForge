package com.cucumberforge.plugin.services;

import com.cucumberforge.plugin.model.ProjectConfig;
import com.cucumberforge.plugin.util.JavaCodeGenerator;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service that generates the full Cucumber BDD project boilerplate.
 */
@Service(Service.Level.PROJECT)
public final class BoilerplateService {

    private final Project project;

    public BoilerplateService(@NotNull Project project) {
        this.project = project;
    }

    public static BoilerplateService getInstance(@NotNull Project project) {
        return project.getService(BoilerplateService.class);
    }

    /**
     * Generate the complete Cucumber boilerplate structure.
     */
    public void generateBoilerplate(ProjectConfig config) {
        WriteCommandAction.runWriteCommandAction(project, "Generate Cucumber Boilerplate", null, () -> {
            try {
                VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
                if (baseDir == null) return;

                String packagePath = config.getBasePackage().replace('.', '/');

                // 1. Create directory structure
                VirtualFile testJava = createDirectories(baseDir, "src/test/java/" + packagePath);
                VirtualFile stepsDir = createDirectories(testJava, config.getStepsPackage());
                VirtualFile configDir = createDirectories(testJava, config.getConfigPackage());
                VirtualFile supportDir = createDirectories(testJava, config.getSupportPackage());
                VirtualFile featuresDir = createDirectories(baseDir, config.getFeaturesDir());

                // 2. Generate Runner
                String runnerCode;
                if (config.getJunitVersion() == ProjectConfig.JUnitVersion.JUNIT5) {
                    runnerCode = JavaCodeGenerator.generateRunnerJUnit5(
                            config.getBasePackage(), config.getRunnerClassName());
                } else {
                    runnerCode = JavaCodeGenerator.generateRunnerJUnit4(
                            config.getBasePackage(), config.getRunnerClassName());
                }
                createFile(testJava, config.getRunnerClassName() + ".java", runnerCode);

                // 3. Generate Spring Configuration
                String springConfig = JavaCodeGenerator.generateSpringConfig(config.getBasePackage());
                createFile(configDir, "CucumberSpringConfiguration.java", springConfig);

                // 4. Generate Testcontainers config if requested
                if (config.isIncludeTestcontainers()
                        && config.getDatabaseType() != ProjectConfig.DatabaseType.H2) {
                    String tcConfig = JavaCodeGenerator.generateTestcontainersConfig(
                            config.getBasePackage(),
                            config.getDatabaseType().name(),
                            config.getDatabaseType().getContainerImage());
                    createFile(configDir, "TestcontainersConfiguration.java", tcConfig);
                }

                // 5. Generate sample feature file
                String sampleFeature = generateSampleFeature();
                createFile(featuresDir, "sample.feature", sampleFeature);

                // 6. Generate application-test.yml
                VirtualFile resources = createDirectories(baseDir, "src/test/resources");
                String testYml = generateTestApplicationYml(config);
                createFile(resources, "application-test.yml", testYml);

                // 7. Add dependencies to build file
                addDependenciesToBuild(baseDir, config);

            } catch (IOException e) {
                throw new RuntimeException("Failed to generate boilerplate: " + e.getMessage(), e);
            }
        });
    }

    private String generateSampleFeature() {
        return "Feature: Sample Feature\n" +
                "  As a user\n" +
                "  I want to verify the application works\n" +
                "  So that I can be confident in my deployment\n\n" +
                "  @smoke\n" +
                "  Scenario: Health check endpoint returns OK\n" +
                "    Given the application is running\n" +
                "    When I call the health check endpoint\n" +
                "    Then the response status should be 200\n" +
                "    And the response body should contain \"UP\"\n";
    }

    private String generateTestApplicationYml(ProjectConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("spring:\n");
        sb.append("  profiles:\n");
        sb.append("    active: test\n");

        if (config.getDatabaseType() == ProjectConfig.DatabaseType.H2) {
            sb.append("  datasource:\n");
            sb.append("    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1\n");
            sb.append("    driver-class-name: org.h2.Driver\n");
            sb.append("    username: sa\n");
            sb.append("    password:\n");
            sb.append("  jpa:\n");
            sb.append("    hibernate:\n");
            sb.append("      ddl-auto: create-drop\n");
            sb.append("    show-sql: true\n");
        } else {
            sb.append("  # Datasource configured via Testcontainers (see TestcontainersConfiguration.java)\n");
            sb.append("  jpa:\n");
            sb.append("    hibernate:\n");
            sb.append("      ddl-auto: create-drop\n");
            sb.append("    show-sql: true\n");
        }

        sb.append("\nserver:\n");
        sb.append("  port: 0  # Random port for tests\n");

        return sb.toString();
    }

    private void addDependenciesToBuild(VirtualFile baseDir, ProjectConfig config) throws IOException {
        // Try Gradle first
        VirtualFile buildFile = baseDir.findChild("build.gradle");
        if (buildFile != null) {
            String content = new String(buildFile.contentsToByteArray(), StandardCharsets.UTF_8);
            if (!content.contains("cucumber")) {
                String deps = buildGradleDependencies(config);
                String marker = "dependencies {";
                int idx = content.indexOf(marker);
                if (idx >= 0) {
                    int insertPos = idx + marker.length();
                    String newContent = content.substring(0, insertPos) + "\n" + deps + content.substring(insertPos);
                    buildFile.setBinaryContent(newContent.getBytes(StandardCharsets.UTF_8));
                }
            }
            return;
        }

        // Try Gradle KTS
        buildFile = baseDir.findChild("build.gradle.kts");
        if (buildFile != null) {
            String content = new String(buildFile.contentsToByteArray(), StandardCharsets.UTF_8);
            if (!content.contains("cucumber")) {
                String deps = buildGradleKtsDependencies(config);
                String marker = "dependencies {";
                int idx = content.indexOf(marker);
                if (idx >= 0) {
                    int insertPos = idx + marker.length();
                    String newContent = content.substring(0, insertPos) + "\n" + deps + content.substring(insertPos);
                    buildFile.setBinaryContent(newContent.getBytes(StandardCharsets.UTF_8));
                }
            }
            return;
        }

        // Try Maven
        VirtualFile pomFile = baseDir.findChild("pom.xml");
        if (pomFile != null) {
            String content = new String(pomFile.contentsToByteArray(), StandardCharsets.UTF_8);
            if (!content.contains("cucumber")) {
                String deps = mavenDependencies(config);
                String marker = "</dependencies>";
                int idx = content.lastIndexOf(marker);
                if (idx >= 0) {
                    String newContent = content.substring(0, idx) + deps + "\n" + content.substring(idx);
                    pomFile.setBinaryContent(newContent.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    private String buildGradleDependencies(ProjectConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("    // Cucumber BDD - Generated by CucumberForge\n");
        sb.append("    testImplementation 'io.cucumber:cucumber-java:7.20.1'\n");
        sb.append("    testImplementation 'io.cucumber:cucumber-spring:7.20.1'\n");

        if (config.getJunitVersion() == ProjectConfig.JUnitVersion.JUNIT5) {
            sb.append("    testImplementation 'io.cucumber:cucumber-junit-platform-engine:7.20.1'\n");
            sb.append("    testImplementation 'org.junit.platform:junit-platform-suite:1.11.3'\n");
        } else {
            sb.append("    testImplementation 'io.cucumber:cucumber-junit:7.20.1'\n");
        }

        if (config.isIncludeTestcontainers()) {
            sb.append("    testImplementation 'org.testcontainers:testcontainers:1.20.4'\n");
            sb.append("    testImplementation 'org.testcontainers:junit-jupiter:1.20.4'\n");
            if (config.getDatabaseType() == ProjectConfig.DatabaseType.POSTGRESQL) {
                sb.append("    testImplementation 'org.testcontainers:postgresql:1.20.4'\n");
            } else if (config.getDatabaseType() == ProjectConfig.DatabaseType.MYSQL) {
                sb.append("    testImplementation 'org.testcontainers:mysql:1.20.4'\n");
            } else if (config.getDatabaseType() == ProjectConfig.DatabaseType.MONGODB) {
                sb.append("    testImplementation 'org.testcontainers:mongodb:1.20.4'\n");
            }
        }

        if (config.isIncludeRestAssured()) {
            sb.append("    testImplementation 'io.rest-assured:rest-assured:5.5.0'\n");
        }

        if (config.getDatabaseType() == ProjectConfig.DatabaseType.H2) {
            sb.append("    testRuntimeOnly 'com.h2database:h2:2.3.232'\n");
        }

        return sb.toString();
    }

    private String buildGradleKtsDependencies(ProjectConfig config) {
        return buildGradleDependencies(config)
                .replace("testImplementation '", "testImplementation(\"")
                .replace("testRuntimeOnly '", "testRuntimeOnly(\"")
                .replace("'\n", "\")\n");
    }

    private String mavenDependencies(ProjectConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("        <!-- Cucumber BDD - Generated by CucumberForge -->\n");
        sb.append(mavenDep("io.cucumber", "cucumber-java", "7.20.1"));
        sb.append(mavenDep("io.cucumber", "cucumber-spring", "7.20.1"));

        if (config.getJunitVersion() == ProjectConfig.JUnitVersion.JUNIT5) {
            sb.append(mavenDep("io.cucumber", "cucumber-junit-platform-engine", "7.20.1"));
            sb.append(mavenDep("org.junit.platform", "junit-platform-suite", "1.11.3"));
        } else {
            sb.append(mavenDep("io.cucumber", "cucumber-junit", "7.20.1"));
        }

        if (config.isIncludeTestcontainers()) {
            sb.append(mavenDep("org.testcontainers", "testcontainers", "1.20.4"));
            sb.append(mavenDep("org.testcontainers", "junit-jupiter", "1.20.4"));
        }

        if (config.isIncludeRestAssured()) {
            sb.append(mavenDep("io.rest-assured", "rest-assured", "5.5.0"));
        }

        return sb.toString();
    }

    private String mavenDep(String groupId, String artifactId, String version) {
        return "        <dependency>\n" +
                "            <groupId>" + groupId + "</groupId>\n" +
                "            <artifactId>" + artifactId + "</artifactId>\n" +
                "            <version>" + version + "</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n";
    }

    // =================== File utilities ===================

    private VirtualFile createDirectories(VirtualFile parent, String path) throws IOException {
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

    private void createFile(VirtualFile dir, String name, String content) throws IOException {
        VirtualFile existing = dir.findChild(name);
        if (existing != null) return; // Don't overwrite existing files
        VirtualFile file = dir.createChildData(this, name);
        file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
    }
}
