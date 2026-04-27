package com.cucumberforge.plugin.services;

import com.cucumberforge.plugin.model.MavenRunConfig;
import com.cucumberforge.plugin.model.MavenRunConfig.RunConfigType;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing Maven run configurations.
 * Persists saved configs and can export them as IntelliJ run configs or shell scripts.
 */
@Service(Service.Level.PROJECT)
@State(name = "CucumberForgeMavenConfigs", storages = @Storage("cucumberforge-maven.xml"))
public final class MavenRunConfigService implements PersistentStateComponent<MavenRunConfigService.ConfigState> {

    private final Project project;
    private ConfigState myState = new ConfigState();

    public MavenRunConfigService(@NotNull Project project) {
        this.project = project;
    }

    public static MavenRunConfigService getInstance(@NotNull Project project) {
        return project.getService(MavenRunConfigService.class);
    }

    @Override
    public @Nullable ConfigState getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull ConfigState state) {
        XmlSerializerUtil.copyBean(state, myState);
    }

    /**
     * Get all saved configurations.
     */
    public List<MavenRunConfig> getConfigs() {
        return myState.configs;
    }

    /**
     * Add a new configuration.
     */
    public void addConfig(MavenRunConfig config) {
        myState.configs.add(config);
    }

    /**
     * Remove a configuration by index.
     */
    public void removeConfig(int index) {
        if (index >= 0 && index < myState.configs.size()) {
            myState.configs.remove(index);
        }
    }

    /**
     * Get default/preset configurations for a typical Cucumber project.
     */
    public List<MavenRunConfig> getPresets() {
        SettingsService settings = SettingsService.getInstance(project);
        String basePackage = settings.getBasePackage().isEmpty() ? "com.example.test" : settings.getBasePackage();

        List<MavenRunConfig> presets = new ArrayList<>();

        presets.add(new MavenRunConfig(
                "Run Application",
                "spring-boot:run",
                "Start the Spring Boot application",
                RunConfigType.RUN_APP
        ));

        presets.add(new MavenRunConfig(
                "Run All Cucumber Tests",
                "test -Dtest=\"" + basePackage + ".CucumberRunnerTest\"",
                "Execute all Cucumber BDD tests",
                RunConfigType.TEST_ALL
        ));

        presets.add(new MavenRunConfig(
                "Run All Tests",
                "test",
                "Execute the full test suite",
                RunConfigType.TEST_ALL
        ));

        presets.add(new MavenRunConfig(
                "Run Tests by Tag @smoke",
                "test -Dcucumber.filter.tags=\"@smoke\"",
                "Execute only tests tagged with @smoke",
                RunConfigType.TEST_TAG
        ));

        presets.add(new MavenRunConfig(
                "Run Tests - Skip Integration",
                "test -DexcludedGroups=integration",
                "Run unit tests only, skip integration tests",
                RunConfigType.TEST_ALL
        ));

        presets.add(new MavenRunConfig(
                "Clean & Test with Report",
                "clean test -Dmaven.test.failure.ignore=true surefire-report:report",
                "Run all tests and generate Surefire HTML report",
                RunConfigType.TEST_ALL
        ));

        presets.add(new MavenRunConfig(
                "Clean Install (skip tests)",
                "clean install -DskipTests",
                "Build the project without running tests",
                RunConfigType.CUSTOM
        ));

        return presets;
    }

    /**
     * Save a configuration as an IntelliJ Run Configuration XML file.
     */
    public boolean saveAsIntelliJRunConfig(MavenRunConfig config) {
        VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return false;

        try {
            VirtualFile ideaDir = baseDir.findChild(".idea");
            if (ideaDir == null) {
                ideaDir = baseDir.createChildDirectory(this, ".idea");
            }
            VirtualFile runConfigDir = ideaDir.findChild("runConfigurations");
            if (runConfigDir == null) {
                runConfigDir = ideaDir.createChildDirectory(this, "runConfigurations");
            }

            String fileName = sanitizeFileName(config.getName()) + ".xml";
            VirtualFile existing = runConfigDir.findChild(fileName);
            if (existing != null) {
                existing.delete(this);
            }

            String xmlContent = config.toIntelliJRunConfigXml(baseDir.getPath());
            VirtualFile configFile = runConfigDir.createChildData(this, fileName);
            configFile.setBinaryContent(xmlContent.getBytes(StandardCharsets.UTF_8));

            return true;
        } catch (IOException e) {
            CucumberUtils.notify(project,
                    "Failed to save run configuration: " + e.getMessage(),
                    NotificationType.ERROR);
            return false;
        }
    }

    /**
     * Save all configurations as a shell script.
     */
    public boolean saveAsShellScript(List<MavenRunConfig> configs) {
        VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return false;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("#!/bin/bash\n");
            sb.append("# CucumberForge Maven Run Configurations\n");
            sb.append("# Generated by CucumberForge Plugin\n\n");

            sb.append("function show_menu() {\n");
            sb.append("    echo \"=== CucumberForge Maven Commands ===\"\n");
            sb.append("    echo \"\"\n");
            for (int i = 0; i < configs.size(); i++) {
                MavenRunConfig c = configs.get(i);
                sb.append("    echo \"  ").append(i + 1).append(") ").append(c.getName()).append("\"\n");
                sb.append("    echo \"     ").append(c.getDescription()).append("\"\n");
                sb.append("    echo \"     -> mvn ").append(c.getCommand()).append("\"\n");
                sb.append("    echo \"\"\n");
            }
            sb.append("    echo \"  0) Exit\"\n");
            sb.append("    echo \"\"\n");
            sb.append("}\n\n");

            // Direct run functions
            for (int i = 0; i < configs.size(); i++) {
                MavenRunConfig c = configs.get(i);
                String funcName = sanitizeFileName(c.getName()).replace("-", "_").toLowerCase();
                sb.append("function run_").append(funcName).append("() {\n");
                sb.append("    echo \"Running: ").append(c.getName()).append("\"\n");
                sb.append("    mvn ").append(c.getCommand()).append("\n");
                sb.append("}\n\n");
            }

            // Run single test function
            sb.append("function run_single_test() {\n");
            sb.append("    if [ -z \"$1\" ]; then\n");
            sb.append("        echo \"Usage: $0 single-test <fully.qualified.TestClass>\"\n");
            sb.append("        exit 1\n");
            sb.append("    fi\n");
            sb.append("    echo \"Running single test: $1\"\n");
            sb.append("    mvn test -Dtest=\"$1\"\n");
            sb.append("}\n\n");

            // Run by tag function
            sb.append("function run_by_tag() {\n");
            sb.append("    if [ -z \"$1\" ]; then\n");
            sb.append("        echo \"Usage: $0 tag <@tagName>\"\n");
            sb.append("        exit 1\n");
            sb.append("    fi\n");
            sb.append("    echo \"Running tests with tag: $1\"\n");
            sb.append("    mvn test -Dcucumber.filter.tags=\"$1\"\n");
            sb.append("}\n\n");

            // Main menu
            sb.append("# Command-line interface\n");
            sb.append("case \"$1\" in\n");
            sb.append("    single-test)\n");
            sb.append("        run_single_test \"$2\"\n");
            sb.append("        ;;\n");
            sb.append("    tag)\n");
            sb.append("        run_by_tag \"$2\"\n");
            sb.append("        ;;\n");
            for (int i = 0; i < configs.size(); i++) {
                String funcName = sanitizeFileName(configs.get(i).getName()).replace("-", "_").toLowerCase();
                sb.append("    ").append(i + 1).append(")\n");
                sb.append("        run_").append(funcName).append("\n");
                sb.append("        ;;\n");
            }
            sb.append("    *)\n");
            sb.append("        show_menu\n");
            sb.append("        read -p \"Select option: \" choice\n");
            sb.append("        case \"$choice\" in\n");
            for (int i = 0; i < configs.size(); i++) {
                String funcName = sanitizeFileName(configs.get(i).getName()).replace("-", "_").toLowerCase();
                sb.append("            ").append(i + 1).append(") run_").append(funcName).append(" ;;\n");
            }
            sb.append("            0) exit 0 ;;\n");
            sb.append("            *) echo \"Invalid option\" ;;\n");
            sb.append("        esac\n");
            sb.append("        ;;\n");
            sb.append("esac\n");

            VirtualFile script = baseDir.findChild("cucumber-run.sh");
            if (script != null) {
                script.delete(this);
            }
            script = baseDir.createChildData(this, "cucumber-run.sh");
            script.setBinaryContent(sb.toString().getBytes(StandardCharsets.UTF_8));

            return true;
        } catch (IOException e) {
            CucumberUtils.notify(project,
                    "Failed to save shell script: " + e.getMessage(),
                    NotificationType.ERROR);
            return false;
        }
    }

    /**
     * Build a Maven command to run a single test class.
     */
    public static String buildSingleTestCommand(String testClassName) {
        return "test -Dtest=\"" + testClassName + "\"";
    }

    /**
     * Build a Maven command to run tests matching a Cucumber tag.
     */
    public static String buildTagTestCommand(String tag) {
        return "test -Dcucumber.filter.tags=\"" + tag + "\"";
    }

    /**
     * Build a Maven command to run a single scenario by name.
     */
    public static String buildScenarioTestCommand(String scenarioName) {
        return "test -Dcucumber.filter.name=\"" + scenarioName + "\"";
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * State class for persistence.
     */
    public static class ConfigState {
        public List<MavenRunConfig> configs = new ArrayList<>();
    }
}
