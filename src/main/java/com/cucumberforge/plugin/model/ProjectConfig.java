package com.cucumberforge.plugin.model;

/**
 * Configuration for Cucumber project setup.
 * Extended with HTTP client, extra libraries, and code style options.
 */
public class ProjectConfig {

    private String basePackage = "com.example.test";
    private JUnitVersion junitVersion = JUnitVersion.JUNIT5;
    private DatabaseType databaseType = DatabaseType.H2;
    private boolean includeTestcontainers = true;
    private boolean includeRestAssured = true;
    private String runnerClassName = "CucumberRunnerTest";
    private String featuresDir = "src/test/resources/features";
    private String stepsPackage = "steps";
    private String configPackage = "config";
    private String supportPackage = "support";

    // New: HTTP Client selection
    private HttpClientType httpClientType = HttpClientType.WEB_TEST_CLIENT;
    private boolean includeMockMvc = false;

    // New: Additional test libraries
    private boolean includeSpringSecurityTest = false;
    private boolean includeWireMock = false;
    private boolean includeAwaitility = false;

    // New: Code style
    private boolean includeLombok = false;
    private boolean includeAssertJ = true;

    public ProjectConfig() {}

    // --- Getters and setters ---

    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }

    public JUnitVersion getJunitVersion() { return junitVersion; }
    public void setJunitVersion(JUnitVersion junitVersion) { this.junitVersion = junitVersion; }

    public DatabaseType getDatabaseType() { return databaseType; }
    public void setDatabaseType(DatabaseType databaseType) { this.databaseType = databaseType; }

    public boolean isIncludeTestcontainers() { return includeTestcontainers; }
    public void setIncludeTestcontainers(boolean includeTestcontainers) { this.includeTestcontainers = includeTestcontainers; }

    public boolean isIncludeRestAssured() { return includeRestAssured; }
    public void setIncludeRestAssured(boolean includeRestAssured) { this.includeRestAssured = includeRestAssured; }

    public String getRunnerClassName() { return runnerClassName; }
    public void setRunnerClassName(String runnerClassName) { this.runnerClassName = runnerClassName; }

    public String getFeaturesDir() { return featuresDir; }
    public void setFeaturesDir(String featuresDir) { this.featuresDir = featuresDir; }

    public String getStepsPackage() { return stepsPackage; }
    public void setStepsPackage(String stepsPackage) { this.stepsPackage = stepsPackage; }

    public String getConfigPackage() { return configPackage; }
    public void setConfigPackage(String configPackage) { this.configPackage = configPackage; }

    public String getSupportPackage() { return supportPackage; }
    public void setSupportPackage(String supportPackage) { this.supportPackage = supportPackage; }

    public HttpClientType getHttpClientType() { return httpClientType; }
    public void setHttpClientType(HttpClientType httpClientType) { this.httpClientType = httpClientType; }

    public boolean isIncludeMockMvc() { return includeMockMvc; }
    public void setIncludeMockMvc(boolean includeMockMvc) { this.includeMockMvc = includeMockMvc; }

    public boolean isIncludeSpringSecurityTest() { return includeSpringSecurityTest; }
    public void setIncludeSpringSecurityTest(boolean v) { this.includeSpringSecurityTest = v; }

    public boolean isIncludeWireMock() { return includeWireMock; }
    public void setIncludeWireMock(boolean includeWireMock) { this.includeWireMock = includeWireMock; }

    public boolean isIncludeAwaitility() { return includeAwaitility; }
    public void setIncludeAwaitility(boolean includeAwaitility) { this.includeAwaitility = includeAwaitility; }

    public boolean isIncludeLombok() { return includeLombok; }
    public void setIncludeLombok(boolean includeLombok) { this.includeLombok = includeLombok; }

    public boolean isIncludeAssertJ() { return includeAssertJ; }
    public void setIncludeAssertJ(boolean includeAssertJ) { this.includeAssertJ = includeAssertJ; }

    // --- Enums ---

    public enum JUnitVersion {
        JUNIT4("JUnit 4"),
        JUNIT5("JUnit 5 (Platform Suite)");

        private final String display;
        JUnitVersion(String display) { this.display = display; }
        public String getDisplay() { return display; }
        @Override public String toString() { return display; }
    }

    public enum DatabaseType {
        H2("H2 (In-Memory)", ""),
        POSTGRESQL("PostgreSQL", "postgres:16-alpine"),
        MYSQL("MySQL", "mysql:8.0"),
        MONGODB("MongoDB", "mongo:7.0"),
        MARIADB("MariaDB", "mariadb:11");

        private final String display;
        private final String containerImage;

        DatabaseType(String display, String containerImage) {
            this.display = display;
            this.containerImage = containerImage;
        }

        public String getDisplay() { return display; }
        public String getContainerImage() { return containerImage; }
        @Override public String toString() { return display; }
    }

    public enum HttpClientType {
        WEB_TEST_CLIENT("WebTestClient"),
        MOCK_MVC("MockMvc"),
        REST_ASSURED("REST-Assured"),
        NONE("None");

        private final String display;
        HttpClientType(String display) { this.display = display; }
        public String getDisplay() { return display; }
        @Override public String toString() { return display; }
    }

    public enum AiProvider {
        OPENAI("OpenAI"),
        COPILOT("GitHub Copilot");

        private final String display;
        AiProvider(String display) { this.display = display; }
        public String getDisplay() { return display; }
        @Override public String toString() { return display; }
    }
}
