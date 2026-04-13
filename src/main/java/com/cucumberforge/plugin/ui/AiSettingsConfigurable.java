package com.cucumberforge.plugin.ui;

import com.cucumberforge.plugin.services.SettingsService;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Settings page for CucumberForge AI configuration.
 * Located at File > Settings > Tools > CucumberForge
 */
public class AiSettingsConfigurable implements Configurable {

    private final Project project;

    // UI components
    private ComboBox<String> providerCombo;
    private JBPasswordField apiKeyField;
    private JBTextField modelField;
    private JBTextField baseUrlField;
    private JBTextArea customPromptArea;
    private JBTextField basePackageField;

    // Provider cards
    private JPanel openAiPanel;
    private JPanel copilotPanel;
    private CardLayout providerCardLayout;
    private JPanel providerCards;

    public AiSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "CucumberForge";
    }

    @Override
    public @Nullable JComponent createComponent() {
        SettingsService settings = SettingsService.getInstance(project);
        SettingsService.State state = settings.getState();

        // Provider selector
        providerCombo = new ComboBox<>(new String[]{"OPENAI", "COPILOT"});
        providerCombo.setSelectedItem(state.aiProvider);

        // OpenAI fields
        apiKeyField = new JBPasswordField();
        apiKeyField.setText(state.openAiApiKey);
        apiKeyField.setColumns(40);

        modelField = new JBTextField(state.openAiModel);
        modelField.setColumns(20);

        baseUrlField = new JBTextField(state.openAiBaseUrl);
        baseUrlField.setColumns(40);

        openAiPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("API Key:"), apiKeyField)
                .addLabeledComponent(new JBLabel("Model:"), modelField)
                .addLabeledComponent(new JBLabel("Base URL:"), baseUrlField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        // Copilot panel
        JTextArea copilotInfo = new JTextArea(
                "GitHub Copilot uses your existing IDE authentication.\n\n" +
                "Requirements:\n" +
                "  • GitHub Copilot plugin installed and enabled\n" +
                "  • Signed in to GitHub with an active Copilot subscription\n\n" +
                "No additional configuration needed."
        );
        copilotInfo.setEditable(false);
        copilotInfo.setOpaque(false);
        copilotInfo.setFont(copilotInfo.getFont().deriveFont(Font.PLAIN));

        copilotPanel = new JPanel(new BorderLayout());
        copilotPanel.add(copilotInfo, BorderLayout.CENTER);

        // Card layout for provider-specific settings
        providerCardLayout = new CardLayout();
        providerCards = new JPanel(providerCardLayout);
        providerCards.add(openAiPanel, "OPENAI");
        providerCards.add(copilotPanel, "COPILOT");
        providerCardLayout.show(providerCards, state.aiProvider);

        providerCombo.addActionListener(e -> {
            String selected = (String) providerCombo.getSelectedItem();
            providerCardLayout.show(providerCards, selected);
        });

        // Custom prompt prefix
        customPromptArea = new JBTextArea(state.customPromptPrefix, 4, 50);
        customPromptArea.setLineWrap(true);
        customPromptArea.setWrapStyleWord(true);
        JScrollPane promptScroll = new JScrollPane(customPromptArea);

        // Base package
        basePackageField = new JBTextField(state.basePackage);
        basePackageField.setColumns(30);

        // Main form
        JPanel mainPanel = FormBuilder.createFormBuilder()
                .addSeparator()
                .addComponent(new JBLabel("AI Configuration"), 8)
                .addVerticalGap(4)
                .addLabeledComponent(new JBLabel("AI Provider:"), providerCombo)
                .addVerticalGap(8)
                .addComponent(providerCards)
                .addVerticalGap(16)
                .addSeparator()
                .addComponent(new JBLabel("General Settings"), 8)
                .addVerticalGap(4)
                .addLabeledComponent(new JBLabel("Base Test Package:"), basePackageField)
                .addVerticalGap(8)
                .addLabeledComponent(new JBLabel("Custom Prompt Prefix:"), promptScroll)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        SettingsService settings = SettingsService.getInstance(project);
        SettingsService.State state = settings.getState();

        return !providerCombo.getSelectedItem().equals(state.aiProvider)
                || !new String(apiKeyField.getPassword()).equals(state.openAiApiKey)
                || !modelField.getText().equals(state.openAiModel)
                || !baseUrlField.getText().equals(state.openAiBaseUrl)
                || !customPromptArea.getText().equals(state.customPromptPrefix)
                || !basePackageField.getText().equals(state.basePackage);
    }

    @Override
    public void apply() throws ConfigurationException {
        SettingsService settings = SettingsService.getInstance(project);

        settings.setAiProvider((String) providerCombo.getSelectedItem());
        settings.setOpenAiApiKey(new String(apiKeyField.getPassword()));
        settings.setOpenAiModel(modelField.getText());
        settings.getState().openAiBaseUrl = baseUrlField.getText();
        settings.getState().customPromptPrefix = customPromptArea.getText();
        settings.setBasePackage(basePackageField.getText());
    }

    @Override
    public void reset() {
        SettingsService settings = SettingsService.getInstance(project);
        SettingsService.State state = settings.getState();

        providerCombo.setSelectedItem(state.aiProvider);
        apiKeyField.setText(state.openAiApiKey);
        modelField.setText(state.openAiModel);
        baseUrlField.setText(state.openAiBaseUrl);
        customPromptArea.setText(state.customPromptPrefix);
        basePackageField.setText(state.basePackage);
        providerCardLayout.show(providerCards, state.aiProvider);
    }
}
