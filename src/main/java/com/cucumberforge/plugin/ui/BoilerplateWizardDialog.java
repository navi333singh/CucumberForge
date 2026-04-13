package com.cucumberforge.plugin.ui;

import com.cucumberforge.plugin.model.ProjectConfig;
import com.cucumberforge.plugin.model.ProjectConfig.*;
import com.cucumberforge.plugin.services.SettingsService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog wizard for configuring Cucumber boilerplate generation.
 */
public class BoilerplateWizardDialog extends DialogWrapper {

    private final Project project;

    private JBTextField packageField;
    private ComboBox<JUnitVersion> junitCombo;
    private ComboBox<DatabaseType> databaseCombo;
    private JBCheckBox testcontainersCheckbox;
    private JBCheckBox restAssuredCheckbox;
    private JBTextField runnerNameField;

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

        packageField = new JBTextField(settings.getBasePackage().isEmpty()
                ? "com.example.test" : settings.getBasePackage());
        packageField.setColumns(35);

        junitCombo = new ComboBox<>(JUnitVersion.values());
        junitCombo.setSelectedItem(JUnitVersion.JUNIT5);

        databaseCombo = new ComboBox<>(DatabaseType.values());
        databaseCombo.setSelectedItem(DatabaseType.POSTGRESQL);

        testcontainersCheckbox = new JBCheckBox("Include Testcontainers", true);
        restAssuredCheckbox = new JBCheckBox("Include REST-Assured", true);

        runnerNameField = new JBTextField("CucumberRunnerTest");
        runnerNameField.setColumns(25);

        // Wire database combo to testcontainers checkbox state
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

        // Build form
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Base Test Package:"), packageField)
                .addVerticalGap(8)
                .addLabeledComponent(new JBLabel("JUnit Version:"), junitCombo)
                .addVerticalGap(8)
                .addLabeledComponent(new JBLabel("Database Type:"), databaseCombo)
                .addVerticalGap(8)
                .addComponent(testcontainersCheckbox)
                .addComponent(restAssuredCheckbox)
                .addVerticalGap(8)
                .addLabeledComponent(new JBLabel("Runner Class Name:"), runnerNameField)
                .addVerticalGap(12)
                .addComponent(createInfoPanel())
                .getPanel();

        panel.setPreferredSize(new Dimension(500, 350));
        return panel;
    }

    private JPanel createInfoPanel() {
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("Generated Structure"));
        JTextArea info = new JTextArea(
                "The following will be generated:\n" +
                "  ├── src/test/java/<package>/\n" +
                "  │   ├── CucumberRunnerTest.java\n" +
                "  │   ├── config/CucumberSpringConfiguration.java\n" +
                "  │   ├── steps/  (step definitions)\n" +
                "  │   └── support/  (test utilities)\n" +
                "  ├── src/test/resources/\n" +
                "  │   ├── features/sample.feature\n" +
                "  │   └── application-test.yml\n" +
                "  └── build.gradle (dependencies added)"
        );
        info.setEditable(false);
        info.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        info.setOpaque(false);
        info.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        infoPanel.add(info, BorderLayout.CENTER);
        return infoPanel;
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

        // Persist base package setting
        SettingsService.getInstance(project).setBasePackage(config.getBasePackage());

        return config;
    }

    @Override
    protected void doOKAction() {
        // Validate
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
