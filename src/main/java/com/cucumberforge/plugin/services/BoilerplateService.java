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
 * Supports extended options: HTTP clients, WireMock, Spring Security, etc.
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

                // 5. Generate HTTP Client base class
                if (config.getHttpClientType() == ProjectConfig.HttpClientType.WEB_TEST_CLIENT) {
                    createFile(supportDir, "BaseWebTestClient.java",
                            generateWebTestClientBase(config.getBasePackage()));
                } else if (config.getHttpClientType() == ProjectConfig.HttpClientType.MOCK_MVC
                        || config.isIncludeMockMvc()) {
                    createFile(supportDir, "BaseMockMvcTest.java",
                            generateMockMvcBase(config.getBasePackage()));
                }

                // 6. Generate WireMock config if requested
                if (config.isIncludeWireMock()) {
                    createFile(configDir, "WireMockConfiguration.java",
                            generateWireMockConfig(config.getBasePackage()));
                }

                // 7. Generate sample feature file
                String sampleFeature = generateSampleFeature(config);
                createFile(featuresDir, "sample.feature", sampleFeature);

                // 8. Generate sample step definitions
                String sampleSteps = generateSampleSteps(config);
                createFile(stepsDir, "SampleStepDefinitions.java", sampleSteps);

                // 9. Generate application-test.yml
                VirtualFile resources = createDirectories(baseDir, "src/test/resources");
                String testYml = generateTestApplicationYml(config);
                createFile(resources, "application-test.yml", testYml);

                // 10. Add dependencies to build file
                addDependenciesToBuild(baseDir, config);

            } catch (IOException e) {
                throw new RuntimeException("Failed to generate boilerplate: " + e.getMessage(), e);
            }
        });
    }

    // =================== Template generators ===================

    private String generateSampleFeature(ProjectConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("Feature: Sample Feature\n");
        sb.append("  As a user\n");
        sb.append("  I want to verify the application works\n");
        sb.append("  So that I can be confident in my deployment\n\n");
        sb.append("  @smoke\n");
        sb.append("  Scenario: Health check endpoint returns OK\n");
        sb.append("    Given the application is running\n");
        sb.append("    When I call the health check endpoint\n");
        sb.append("    Then the response status should be 200\n");
        sb.append("    And the response body should contain \"UP\"\n\n");
        sb.append("  @smoke\n");
        sb.append("  Scenario Outline: API returns correct status codes\n");
        sb.append("    Given the application is running\n");
        sb.append("    When I send a <method> request to \"<endpoint>\"\n");
        sb.append("    Then the response status should be <status>\n\n");
        sb.append("    Examples:\n");
        sb.append("      | method | endpoint         | status |\n");
        sb.append("      | GET    | /actuator/health | 200    |\n");
        sb.append("      | GET    | /api/nonexistent | 404    |\n");
        return sb.toString();
    }

    private String generateSampleSteps(ProjectConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(config.getBasePackage()).append(".steps;\n\n");

        // Imports based on HTTP client type
        switch (config.getHttpClientType()) {
            case WEB_TEST_CLIENT:
                sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
                sb.append("import org.springframework.test.web.reactive.server.WebTestClient;\n");
                sb.append("import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;\n");
                break;
            case MOCK_MVC:
                sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
                sb.append("import org.springframework.test.web.servlet.MockMvc;\n");
                sb.append("import org.springframework.test.web.servlet.MvcResult;\n");
                sb.append("import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;\n");
                sb.append("import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;\n");
                break;
            case REST_ASSURED:
                sb.append("import io.restassured.RestAssured;\n");
                sb.append("import io.restassured.response.Response;\n");
                sb.append("import org.springframework.boot.test.web.server.LocalServerPort;\n");
                break;
            default:
                sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
                break;
        }

        sb.append("import io.cucumber.java.en.Given;\n");
        sb.append("import io.cucumber.java.en.When;\n");
        sb.append("import io.cucumber.java.en.Then;\n");
        sb.append("import io.cucumber.java.en.And;\n");
        if (config.isIncludeAssertJ()) {
            sb.append("import static org.assertj.core.api.Assertions.assertThat;\n");
        }
        sb.append("\n");
        sb.append("/**\n");
        sb.append(" * Sample step definitions generated by CucumberForge.\n");
        sb.append(" */\n");
        sb.append("public class SampleStepDefinitions {\n\n");

        // Field declarations based on HTTP client
        switch (config.getHttpClientType()) {
            case WEB_TEST_CLIENT:
                sb.append("    @Autowired\n");
                sb.append("    private WebTestClient webTestClient;\n\n");
                sb.append("    private ResponseSpec lastResponse;\n\n");
                break;
            case MOCK_MVC:
                sb.append("    @Autowired\n");
                sb.append("    private MockMvc mockMvc;\n\n");
                sb.append("    private MvcResult lastResult;\n\n");
                break;
            case REST_ASSURED:
                sb.append("    @LocalServerPort\n");
                sb.append("    private int port;\n\n");
                sb.append("    private Response lastResponse;\n\n");
                break;
        }

        // Step implementations
        sb.append("    @Given(\"the application is running\")\n");
        sb.append("    public void theApplicationIsRunning() {\n");
        sb.append("        // Application started by Spring Boot test context\n");
        sb.append("    }\n\n");

        sb.append("    @When(\"I call the health check endpoint\")\n");
        sb.append("    public void iCallTheHealthCheckEndpoint() {\n");
        switch (config.getHttpClientType()) {
            case WEB_TEST_CLIENT:
                sb.append("        lastResponse = webTestClient.get()\n");
                sb.append("                .uri(\"/actuator/health\")\n");
                sb.append("                .exchange();\n");
                break;
            case MOCK_MVC:
                sb.append("        try {\n");
                sb.append("            lastResult = mockMvc.perform(get(\"/actuator/health\"))\n");
                sb.append("                    .andReturn();\n");
                sb.append("        } catch (Exception e) {\n");
                sb.append("            throw new RuntimeException(e);\n");
                sb.append("        }\n");
                break;
            case REST_ASSURED:
                sb.append("        lastResponse = RestAssured.given()\n");
                sb.append("                .port(port)\n");
                sb.append("                .get(\"/actuator/health\");\n");
                break;
            default:
                sb.append("        // TODO: implement HTTP call\n");
                break;
        }
        sb.append("    }\n\n");

        sb.append("    @When(\"I send a {word} request to {string}\")\n");
        sb.append("    public void iSendARequest(String method, String endpoint) {\n");
        switch (config.getHttpClientType()) {
            case WEB_TEST_CLIENT:
                sb.append("        lastResponse = webTestClient.method(org.springframework.http.HttpMethod.valueOf(method))\n");
                sb.append("                .uri(endpoint)\n");
                sb.append("                .exchange();\n");
                break;
            case REST_ASSURED:
                sb.append("        lastResponse = RestAssured.given()\n");
                sb.append("                .port(port)\n");
                sb.append("                .request(method, endpoint);\n");
                break;
            default:
                sb.append("        // TODO: implement dynamic HTTP method call\n");
                break;
        }
        sb.append("    }\n\n");

        sb.append("    @Then(\"the response status should be {int}\")\n");
        sb.append("    public void theResponseStatusShouldBe(int expectedStatus) {\n");
        switch (config.getHttpClientType()) {
            case WEB_TEST_CLIENT:
                sb.append("        lastResponse.expectStatus().isEqualTo(expectedStatus);\n");
                break;
            case MOCK_MVC:
                if (config.isIncludeAssertJ()) {
                    sb.append("        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expectedStatus);\n");
                } else {
                    sb.append("        assert lastResult.getResponse().getStatus() == expectedStatus;\n");
                }
                break;
            case REST_ASSURED:
                sb.append("        lastResponse.then().statusCode(expectedStatus);\n");
                break;
            default:
                sb.append("        // TODO: verify status code\n");
                break;
        }
        sb.append("    }\n\n");

        sb.append("    @And(\"the response body should contain {string}\")\n");
        sb.append("    public void theResponseBodyShouldContain(String expectedText) {\n");
        switch (config.getHttpClientType()) {
            case WEB_TEST_CLIENT:
                sb.append("        lastResponse.expectBody(String.class)\n");
                sb.append("                .consumeWith(body -> assertThat(body.getResponseBody()).contains(expectedText));\n");
                break;
            case REST_ASSURED:
                sb.append("        lastResponse.then().body(org.hamcrest.Matchers.containsString(expectedText));\n");
                break;
            default:
                sb.append("        // TODO: verify body content\n");
                break;
        }
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String generateWebTestClientBase(String packageName) {
        return "package " + packageName + ".support;\n\n" +
                "import org.springframework.beans.factory.annotation.Autowired;\n" +
                "import org.springframework.test.web.reactive.server.WebTestClient;\n\n" +
                "/**\n" +
                " * Base class providing WebTestClient for step definitions.\n" +
                " * Generated by CucumberForge.\n" +
                " */\n" +
                "public abstract class BaseWebTestClient {\n\n" +
                "    @Autowired\n" +
                "    protected WebTestClient webTestClient;\n\n" +
                "    protected WebTestClient.ResponseSpec lastResponse;\n\n" +
                "    protected void doGet(String uri) {\n" +
                "        lastResponse = webTestClient.get().uri(uri).exchange();\n" +
                "    }\n\n" +
                "    protected void doPost(String uri, String body) {\n" +
                "        lastResponse = webTestClient.post().uri(uri)\n" +
                "                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)\n" +
                "                .bodyValue(body)\n" +
                "                .exchange();\n" +
                "    }\n\n" +
                "    protected void doDelete(String uri) {\n" +
                "        lastResponse = webTestClient.delete().uri(uri).exchange();\n" +
                "    }\n\n" +
                "    protected void doPut(String uri, String body) {\n" +
                "        lastResponse = webTestClient.put().uri(uri)\n" +
                "                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)\n" +
                "                .bodyValue(body)\n" +
                "                .exchange();\n" +
                "    }\n" +
                "}\n";
    }

    private String generateMockMvcBase(String packageName) {
        return "package " + packageName + ".support;\n\n" +
                "import org.springframework.beans.factory.annotation.Autowired;\n" +
                "import org.springframework.test.web.servlet.MockMvc;\n" +
                "import org.springframework.test.web.servlet.MvcResult;\n" +
                "import org.springframework.http.MediaType;\n\n" +
                "import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;\n\n" +
                "/**\n" +
                " * Base class providing MockMvc for step definitions.\n" +
                " * Generated by CucumberForge.\n" +
                " */\n" +
                "public abstract class BaseMockMvcTest {\n\n" +
                "    @Autowired\n" +
                "    protected MockMvc mockMvc;\n\n" +
                "    protected MvcResult lastResult;\n\n" +
                "    protected void doGet(String uri) throws Exception {\n" +
                "        lastResult = mockMvc.perform(get(uri)).andReturn();\n" +
                "    }\n\n" +
                "    protected void doPost(String uri, String body) throws Exception {\n" +
                "        lastResult = mockMvc.perform(post(uri)\n" +
                "                .contentType(MediaType.APPLICATION_JSON)\n" +
                "                .content(body)).andReturn();\n" +
                "    }\n\n" +
                "    protected void doDelete(String uri) throws Exception {\n" +
                "        lastResult = mockMvc.perform(delete(uri)).andReturn();\n" +
                "    }\n\n" +
                "    protected void doPut(String uri, String body) throws Exception {\n" +
                "        lastResult = mockMvc.perform(put(uri)\n" +
                "                .contentType(MediaType.APPLICATION_JSON)\n" +
                "                .content(body)).andReturn();\n" +
                "    }\n" +
                "}\n";
    }

    private String generateWireMockConfig(String packageName) {
        return "package " + packageName + ".config;\n\n" +
                "import com.github.tomakehurst.wiremock.WireMockServer;\n" +
                "import com.github.tomakehurst.wiremock.core.WireMockConfiguration;\n" +
                "import io.cucumber.java.After;\n" +
                "import io.cucumber.java.Before;\n" +
                "import org.springframework.beans.factory.annotation.Value;\n\n" +
                "/**\n" +
                " * WireMock configuration for mocking external APIs.\n" +
                " * Generated by CucumberForge.\n" +
                " */\n" +
                "public class WireMockConfiguration {\n\n" +
                "    private static WireMockServer wireMockServer;\n\n" +
                "    @Value(\"${wiremock.port:8089}\")\n" +
                "    private int wiremockPort;\n\n" +
                "    @Before(\"@wiremock\")\n" +
                "    public void startWireMock() {\n" +
                "        if (wireMockServer == null || !wireMockServer.isRunning()) {\n" +
                "            wireMockServer = new WireMockServer(\n" +
                "                    WireMockConfiguration.wireMockConfig().port(wiremockPort));\n" +
                "            wireMockServer.start();\n" +
                "        }\n" +
                "    }\n\n" +
                "    @After(\"@wiremock\")\n" +
                "    public void stopWireMock() {\n" +
                "        if (wireMockServer != null && wireMockServer.isRunning()) {\n" +
                "            wireMockServer.stop();\n" +
                "        }\n" +
                "    }\n\n" +
                "    public static WireMockServer getWireMockServer() {\n" +
                "        return wireMockServer;\n" +
                "    }\n" +
                "}\n";
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

        if (config.isIncludeWireMock()) {
            sb.append("\nwiremock:\n");
            sb.append("  port: 8089\n");
        }

        return sb.toString();
    }

    // =================== Dependency management ===================

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

        if (config.isIncludeRestAssured() || config.getHttpClientType() == ProjectConfig.HttpClientType.REST_ASSURED) {
            sb.append("    testImplementation 'io.rest-assured:rest-assured:5.5.0'\n");
        }

        if (config.getDatabaseType() == ProjectConfig.DatabaseType.H2) {
            sb.append("    testRuntimeOnly 'com.h2database:h2:2.3.232'\n");
        }

        if (config.isIncludeAssertJ()) {
            sb.append("    testImplementation 'org.assertj:assertj-core:3.26.3'\n");
        }

        if (config.isIncludeSpringSecurityTest()) {
            sb.append("    testImplementation 'org.springframework.security:spring-security-test'\n");
        }

        if (config.isIncludeWireMock()) {
            sb.append("    testImplementation 'org.wiremock:wiremock-standalone:3.9.2'\n");
        }

        if (config.isIncludeAwaitility()) {
            sb.append("    testImplementation 'org.awaitility:awaitility:4.2.2'\n");
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

        if (config.isIncludeRestAssured() || config.getHttpClientType() == ProjectConfig.HttpClientType.REST_ASSURED) {
            sb.append(mavenDep("io.rest-assured", "rest-assured", "5.5.0"));
        }

        if (config.isIncludeAssertJ()) {
            sb.append(mavenDep("org.assertj", "assertj-core", "3.26.3"));
        }

        if (config.isIncludeSpringSecurityTest()) {
            sb.append(mavenDepNoVersion("org.springframework.security", "spring-security-test"));
        }

        if (config.isIncludeWireMock()) {
            sb.append(mavenDep("org.wiremock", "wiremock-standalone", "3.9.2"));
        }

        if (config.isIncludeAwaitility()) {
            sb.append(mavenDep("org.awaitility", "awaitility", "4.2.2"));
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

    private String mavenDepNoVersion(String groupId, String artifactId) {
        return "        <dependency>\n" +
                "            <groupId>" + groupId + "</groupId>\n" +
                "            <artifactId>" + artifactId + "</artifactId>\n" +
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
