package com.cucumberforge.plugin.ui;

import com.cucumberforge.plugin.model.ProjectConfig;
import com.cucumberforge.plugin.model.ProjectConfig.*;
import com.cucumberforge.plugin.services.SettingsService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Wizard dialog for configuring Cucumber boilerplate generation.
 * Improved UI with grouped sections, modern styling and more options.
 */
public class BoilerplateWizardDialog extends DialogWrapper {

    private final Project project;

    // --- Project Setup ---
    private JBTextField packageField;
    private ComboBox<JUnitVersion> junitCombo;
    private JBTextField runnerNameField;

    // --- Database & Infrastructure ---
    private ComboBox<DatabaseType> databaseCombo;
    private JBCheckBox testcontainersCheckbox;

    // --- HTTP Client ---
    private ComboBox<String> httpClientCombo;
    private JBCheckBox restAssuredCheckbox;

    // --- Test Features ---
    private JBCheckBox mockMvcCheckbox;
    private JBCheckBox springSecurityTestCheckbox;
    private JBCheckBox wiremockCheckbox;
    private JBCheckBox awaitilityCheckbox;

    // --- Code Style ---
    private JBCheckBox lombokCheckbox;
    private JBCheckBox assertJCheckbox;

    public BoilerplateWizardDialog(Project project) {
        super(project, true);
        this.project = project;
        setTitle("CucumberForge — Generate Cucumber Boilerplate");
        setOKButtonText("Generate");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        SettingsService settings = SettingsService.getInstance(project);

        // === Project Setup Section ===
        packageField = new JBTextField(settings.getBasePackage().isEmpty()
                ? "com.example.test" : settings.getBasePackage());
        packageField.setColumns(35);

        junitCombo = new ComboBox<>(JUnitVersion.values());
        junitCombo.setSelectedItem(JUnitVersion.JUNIT5);

        runnerNameField = new JBTextField("CucumberRunnerTest");
        runnerNameField.setColumns(25);

        JPanel projectSection = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Base Test Package:"), packageField)
                .addVerticalGap(6)
                .addLabeledComponent(new JBLabel("JUnit Version:"), junitCombo)
                .addVerticalGap(6)
                .addLabeledComponent(new JBLabel("Runner Class Name:"), runnerNameField)
                .getPanel();

        // === Database & Infrastructure Section ===
        databaseCombo = new ComboBox<>(DatabaseType.values());
        databaseCombo.setSelectedItem(DatabaseType.POSTGRESQL);

        testcontainersCheckbox = new JBCheckBox("Testcontainers (auto-spin up DB in Docker)", true);

        databaseCombo.addActionListener(e -> {
            DatabaseType selected = (DatabaseType) databaseCombo.getSelectedItem();
            if (selected == DatabaseType.H2) {
                testcontainersCheckbox.setSelected(false);
                testcontainersCheckbox.setEnabled(false);
            } else {
                testcontainersCheckbox.setEnabled(true);
                testcontainersCheckbox.setSelected(true);
            }
        });

        JPanel dbSection = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Database:"), databaseCombo)
                .addVerticalGap(4)
                .addComponent(testcontainersCheckbox)
                .getPanel();

        // === HTTP Client Section ===
        httpClientCombo = new ComboBox<>(new String[]{
                "WebTestClient (WebFlux/Reactive)",
                "MockMvc (Servlet/MVC)",
                "RestAssured",
                "None"
        });
        httpClientCombo.setSelectedIndex(0);

        restAssuredCheckbox = new JBCheckBox("Include REST-Assured (additional HTTP testing)", false);
        mockMvcCheckbox = new JBCheckBox("Generate MockMvc base configuration", false);

        httpClientCombo.addActionListener(e -> {
            String sel = (String) httpClientCombo.getSelectedItem();
            if (sel != null) {
                restAssuredCheckbox.setEnabled(!sel.startsWith("RestAssured"));
                if (sel.startsWith("RestAssured")) restAssuredCheckbox.setSelected(true);
                mockMvcCheckbox.setEnabled(!sel.startsWith("MockMvc"));
                if (sel.startsWith("MockMvc")) mockMvcCheckbox.setSelected(true);
            }
        });

        JPanel httpSection = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Primary HTTP Client:"), httpClientCombo)
                .addVerticalGap(4)
                .addComponent(restAssuredCheckbox)
                .addComponent(mockMvcCheckbox)
                .getPanel();

        // === Additional Test Libraries ===
        springSecurityTestCheckbox = new JBCheckBox("Spring Security Test (@WithMockUser, etc.)", false);
        wiremockCheckbox = new JBCheckBox("WireMock (external API mocking)", false);
        awaitilityCheckbox = new JBCheckBox("Awaitility (async testing)", false);

        JPanel extraSection = FormBuilder.createFormBuilder()
                .addComponent(springSecurityTestCheckbox)
                .addComponent(wiremockCheckbox)
                .addComponent(awaitilityCheckbox)
                .getPanel();

        // === Code Style ===
        lombokCheckbox = new JBCheckBox("Use Lombok in generated code", false);
        assertJCheckbox = new JBCheckBox("Include AssertJ (fluent assertions)", true);

        JPanel styleSection = FormBuilder.createFormBuilder()
                .addComponent(lombokCheckbox)
                .addComponent(assertJCheckbox)
                .getPanel();

        // === Generated Structure Preview ===
        JPanel previewPanel = createPreviewPanel();

        // === Assemble all sections ===
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        mainPanel.add(createSection("Project Setup", projectSection));
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(createSection("Database & Infrastructure", dbSection));
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(createSection("HTTP Client & API Testing", httpSection));
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(createSection("Additional Libraries", extraSection));
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(createSection("Code Style", styleSection));
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(previewPanel);

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.setPreferredSize(new Dimension(560, 520));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        return scrollPane;
    }

    private JPanel createSection(String title, JPanel content) {
        JPanel section = new JPanel(new BorderLayout());
        section.setOpaque(false);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height + 200));

        TitledSeparator separator = new TitledSeparator(title);
        section.add(separator, BorderLayout.NORTH);

        JPanel padded = new JPanel(new BorderLayout());
        padded.setBorder(JBUI.Borders.emptyLeft(16));
        padded.setOpaque(false);
        padded.add(content, BorderLayout.CENTER);
        section.add(padded, BorderLayout.CENTER);

        return section;
    }

    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.empty(4, 0),
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(JBColor.border(), 1, true),
                        "Generated Structure"
                )
        ));

        JTextArea info = new JTextArea(
                "  src/test/java/<package>/\n" +
                "    CucumberRunnerTest.java\n" +
                "    config/\n" +
                "      CucumberSpringConfiguration.java\n" +
                "      TestcontainersConfiguration.java  (if enabled)\n" +
                "    steps/   (step definitions)\n" +
                "    support/ (test utilities & builders)\n" +
                "  src/test/resources/\n" +
                "    features/sample.feature\n" +
                "    application-test.yml\n" +
                "  Dependencies added to build file"
        );
        info.setEditable(false);
        info.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        info.setOpaque(false);
        info.setBorder(JBUI.Borders.empty(8));
        info.setForeground(UIUtil.getLabelDisabledForeground());
        panel.add(info, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Get the configured ProjectConfig from the dialog inputs.
     */
    public ProjectConfig getConfig() {
        ProjectConfig config = new ProjectConfig();
        config.setBasePackage(packageField.getText().trim());
        config.setJunitVersion((JUnitVersion) junitCombo.getSelectedItem());
        config.setDatabaseType((DatabaseType) databaseCombo.getSelectedItem());
        config.setIncludeTestcontainers(testcontainersCheckbox.isSelected());
        config.setIncludeRestAssured(restAssuredCheckbox.isSelected());
        config.setRunnerClassName(runnerNameField.getText().trim());

        // New options
        String httpClient = (String) httpClientCombo.getSelectedItem();
        if (httpClient != null) {
            if (httpClient.startsWith("WebTestClient")) config.setHttpClientType(HttpClientType.WEB_TEST_CLIENT);
            else if (httpClient.startsWith("MockMvc")) config.setHttpClientType(HttpClientType.MOCK_MVC);
            else if (httpClient.startsWith("RestAssured")) config.setHttpClientType(HttpClientType.REST_ASSURED);
            else config.setHttpClientType(HttpClientType.NONE);
        }
        config.setIncludeMockMvc(mockMvcCheckbox.isSelected());
        config.setIncludeSpringSecurityTest(springSecurityTestCheckbox.isSelected());
        config.setIncludeWireMock(wiremockCheckbox.isSelected());
        config.setIncludeAwaitility(awaitilityCheckbox.isSelected());
        config.setIncludeLombok(lombokCheckbox.isSelected());
        config.setIncludeAssertJ(assertJCheckbox.isSelected());

        // Persist base package setting
        SettingsService.getInstance(project).setBasePackage(config.getBasePackage());

        return config;
    }

    @Override
    protected void doOKAction() {
        String pkg = packageField.getText().trim();
        if (pkg.isEmpty() || !pkg.matches("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*")) {
            setErrorText("Invalid package name. Use lowercase dot-separated identifiers (e.g., com.example.test)");
            return;
        }
        String runner = runnerNameField.getText().trim();
        if (runner.isEmpty() || !runner.matches("[A-Z][A-Za-z0-9]*")) {
            setErrorText("Runner class name must be a valid Java class name starting with uppercase");
            return;
        }
        super.doOKAction();
    }
}
