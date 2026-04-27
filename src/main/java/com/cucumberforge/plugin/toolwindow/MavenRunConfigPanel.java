package com.cucumberforge.plugin.toolwindow;

import com.cucumberforge.plugin.model.MavenRunConfig;
import com.cucumberforge.plugin.model.MavenRunConfig.RunConfigType;
import com.cucumberforge.plugin.services.MavenRunConfigService;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Panel for managing Maven run configurations.
 * Allows creating, saving as IntelliJ run config, and exporting as shell script.
 */
public class MavenRunConfigPanel {

    private final Project project;
    private final JPanel mainPanel;

    private JBTable configTable;
    private DefaultTableModel tableModel;

    // Add config form fields
    private JBTextField nameField;
    private JBTextField commandField;
    private JBTextField descriptionField;
    private ComboBox<RunConfigType> typeCombo;

    public MavenRunConfigPanel(Project project) {
        this.project = project;
        this.mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(UIUtil.getPanelBackground());
        buildUI();
        refreshData();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    private void buildUI() {
        // --- Header with title ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(JBUI.Borders.empty(12, 16, 4, 16));
        headerPanel.setOpaque(false);

        JBLabel titleLabel = new JBLabel("Maven Run Configurations");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JBLabel subtitleLabel = new JBLabel("Save and share run configurations with your project");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.ITALIC, 11f));
        subtitleLabel.setForeground(UIUtil.getLabelDisabledForeground());
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // --- Config table ---
        tableModel = new DefaultTableModel(
                new String[]{"Name", "Command", "Type", ""}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        configTable = new JBTable(tableModel);
        configTable.setRowHeight(36);
        configTable.setShowGrid(false);
        configTable.setIntercellSpacing(new Dimension(0, 1));
        configTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        configTable.getColumnModel().getColumn(1).setPreferredWidth(320);
        configTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        configTable.getColumnModel().getColumn(3).setPreferredWidth(60);

        // Type column with colored badge
        configTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(getFont().deriveFont(Font.BOLD, 10f));
                String type = String.valueOf(value);
                if (type.contains("App")) {
                    setForeground(new JBColor(0x2196F3, 0x64B5F6));
                } else if (type.contains("Test")) {
                    setForeground(new JBColor(0x4CAF50, 0x81C784));
                } else {
                    setForeground(new JBColor(0xFF9800, 0xFFB74D));
                }
                return c;
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(configTable);
        scrollPane.setBorder(JBUI.Borders.empty(4, 16, 8, 16));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // --- Bottom: Add form + Action buttons ---
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 8));
        bottomPanel.setBorder(JBUI.Borders.empty(4, 16, 12, 16));
        bottomPanel.setOpaque(false);

        // Add config form
        JPanel addPanel = new JPanel(new GridBagLayout());
        addPanel.setOpaque(false);
        addPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(JBColor.border(), 1, true),
                        "Add Configuration"),
                JBUI.Borders.empty(8)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(2, 4);

        nameField = new JBTextField();
        nameField.setColumns(18);
        commandField = new JBTextField();
        commandField.setColumns(25);
        descriptionField = new JBTextField();
        descriptionField.setColumns(25);
        typeCombo = new ComboBox<>(RunConfigType.values());

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        addPanel.add(new JBLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        addPanel.add(nameField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        addPanel.add(new JBLabel("  Type:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        addPanel.add(typeCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        addPanel.add(new JBLabel("Command:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1;
        addPanel.add(commandField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0;
        addPanel.add(new JBLabel("Description:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1;
        addPanel.add(descriptionField, gbc);

        bottomPanel.add(addPanel, BorderLayout.CENTER);

        // Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttonPanel.setOpaque(false);

        JButton addBtn = createStyledButton("Add");
        JButton addPresetsBtn = createStyledButton("Load Presets");
        JButton saveToIdeBtn = createStyledButton("Save to IntelliJ");
        JButton saveAllToIdeBtn = createStyledButton("Save All to IntelliJ");
        JButton exportShellBtn = createStyledButton("Export Shell Script");
        JButton removeBtn = createStyledButton("Remove");

        addBtn.addActionListener(e -> addCustomConfig());
        addPresetsBtn.addActionListener(e -> loadPresets());
        saveToIdeBtn.addActionListener(e -> saveSelectedToIntelliJ());
        saveAllToIdeBtn.addActionListener(e -> saveAllToIntelliJ());
        exportShellBtn.addActionListener(e -> exportShellScript());
        removeBtn.addActionListener(e -> removeSelected());

        buttonPanel.add(addBtn);
        buttonPanel.add(addPresetsBtn);
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(saveToIdeBtn);
        buttonPanel.add(saveAllToIdeBtn);
        buttonPanel.add(exportShellBtn);
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(removeBtn);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    public void refreshData() {
        tableModel.setRowCount(0);
        MavenRunConfigService service = MavenRunConfigService.getInstance(project);
        for (MavenRunConfig config : service.getConfigs()) {
            tableModel.addRow(new Object[]{
                    config.getName(),
                    "mvn " + config.getCommand(),
                    config.getType().getDisplay(),
                    config.getDescription()
            });
        }
    }

    // =================== Actions ===================

    private void addCustomConfig() {
        String name = nameField.getText().trim();
        String command = commandField.getText().trim();
        String desc = descriptionField.getText().trim();
        RunConfigType type = (RunConfigType) typeCombo.getSelectedItem();

        if (name.isEmpty() || command.isEmpty()) {
            CucumberUtils.notify(project, "Name and command are required.", NotificationType.WARNING);
            return;
        }

        // Strip leading "mvn " if user typed it
        if (command.startsWith("mvn ")) command = command.substring(4);

        MavenRunConfig config = new MavenRunConfig(name, command, desc, type);
        MavenRunConfigService.getInstance(project).addConfig(config);
        refreshData();

        nameField.setText("");
        commandField.setText("");
        descriptionField.setText("");
    }

    private void loadPresets() {
        MavenRunConfigService service = MavenRunConfigService.getInstance(project);
        List<MavenRunConfig> presets = service.getPresets();

        for (MavenRunConfig preset : presets) {
            boolean exists = service.getConfigs().stream()
                    .anyMatch(c -> c.getName().equals(preset.getName()));
            if (!exists) {
                service.addConfig(preset);
            }
        }
        refreshData();
        CucumberUtils.notify(project, "Preset configurations loaded!", NotificationType.INFORMATION);
    }

    private void saveSelectedToIntelliJ() {
        int row = configTable.getSelectedRow();
        if (row < 0) {
            CucumberUtils.notify(project, "Select a configuration first.", NotificationType.WARNING);
            return;
        }

        MavenRunConfigService service = MavenRunConfigService.getInstance(project);
        MavenRunConfig config = service.getConfigs().get(row);

        WriteCommandAction.runWriteCommandAction(project, "Save Run Config", null, () -> {
            if (service.saveAsIntelliJRunConfig(config)) {
                CucumberUtils.notify(project,
                        "Run configuration '" + config.getName() + "' saved to .idea/runConfigurations!",
                        NotificationType.INFORMATION);
            }
        });
    }

    private void saveAllToIntelliJ() {
        MavenRunConfigService service = MavenRunConfigService.getInstance(project);
        if (service.getConfigs().isEmpty()) {
            CucumberUtils.notify(project, "No configurations to save.", NotificationType.WARNING);
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, "Save All Run Configs", null, () -> {
            int count = 0;
            for (MavenRunConfig config : service.getConfigs()) {
                if (service.saveAsIntelliJRunConfig(config)) count++;
            }
            CucumberUtils.notify(project,
                    count + " run configuration(s) saved to .idea/runConfigurations!",
                    NotificationType.INFORMATION);
        });
    }

    private void exportShellScript() {
        MavenRunConfigService service = MavenRunConfigService.getInstance(project);
        if (service.getConfigs().isEmpty()) {
            CucumberUtils.notify(project, "No configurations to export.", NotificationType.WARNING);
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, "Export Shell Script", null, () -> {
            if (service.saveAsShellScript(service.getConfigs())) {
                CucumberUtils.notify(project,
                        "Shell script 'cucumber-run.sh' created in project root!",
                        NotificationType.INFORMATION);
            }
        });
    }

    private void removeSelected() {
        int row = configTable.getSelectedRow();
        if (row < 0) return;

        MavenRunConfigService service = MavenRunConfigService.getInstance(project);
        service.removeConfig(row);
        refreshData();
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setFont(button.getFont().deriveFont(11f));
        return button;
    }
}
