package com.cucumberforge.plugin.toolwindow;

import com.cucumberforge.plugin.services.StepRegistryService;
import com.cucumberforge.plugin.util.CucumberUtils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Main BDD Dashboard panel showing overview statistics and feature file tree.
 */
public class BddDashboardPanel {

    private final Project project;
    private final JPanel mainPanel;

    // Stats labels
    private JBLabel featureCountLabel;
    private JBLabel scenarioCountLabel;
    private JBLabel stepDefCountLabel;
    private JBLabel duplicateCountLabel;

    // Feature file table
    private JBTable featureTable;
    private DefaultTableModel featureTableModel;

    public BddDashboardPanel(Project project) {
        this.project = project;
        this.mainPanel = new JPanel(new BorderLayout());
        buildUI();
        refreshData();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    private void buildUI() {
        // --- Stats panel ---
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 12, 0));
        statsPanel.setBorder(JBUI.Borders.empty(12, 16));

        featureCountLabel = createStatCard("Feature Files", "0", new JBColor(0x4CAF50, 0x81C784));
        scenarioCountLabel = createStatCard("Scenarios", "0", new JBColor(0x2196F3, 0x64B5F6));
        stepDefCountLabel = createStatCard("Step Definitions", "0", new JBColor(0xFF9800, 0xFFB74D));
        duplicateCountLabel = createStatCard("Duplicates", "0", new JBColor(0xF44336, 0xE57373));

        statsPanel.add(wrapStatCard(featureCountLabel, new JBColor(0x4CAF50, 0x81C784)));
        statsPanel.add(wrapStatCard(scenarioCountLabel, new JBColor(0x2196F3, 0x64B5F6)));
        statsPanel.add(wrapStatCard(stepDefCountLabel, new JBColor(0xFF9800, 0xFFB74D)));
        statsPanel.add(wrapStatCard(duplicateCountLabel, new JBColor(0xF44336, 0xE57373)));

        mainPanel.add(statsPanel, BorderLayout.NORTH);

        // --- Feature file table ---
        featureTableModel = new DefaultTableModel(
                new String[]{"Feature File", "Scenarios", "Steps", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        featureTable = new JBTable(featureTableModel);
        featureTable.setRowHeight(28);
        featureTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        featureTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        featureTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        featureTable.getColumnModel().getColumn(3).setPreferredWidth(100);

        // Status column renderer
        featureTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if ("✅ Complete".equals(value)) {
                    setForeground(new JBColor(0x4CAF50, 0x81C784));
                } else if ("⚠️ Partial".equals(value)) {
                    setForeground(new JBColor(0xFF9800, 0xFFB74D));
                } else {
                    setForeground(new JBColor(0xF44336, 0xE57373));
                }
                return c;
            }
        });

        // Double-click to open file
        featureTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = featureTable.getSelectedRow();
                    if (row >= 0) {
                        String fileName = (String) featureTableModel.getValueAt(row, 0);
                        openFeatureFile(fileName);
                    }
                }
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(featureTable);
        scrollPane.setBorder(JBUI.Borders.empty(0, 16, 12, 16));

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // --- Refresh button ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBorder(JBUI.Borders.empty(4, 16));
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshData());
        bottomPanel.add(refreshButton);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    public void refreshData() {
        // Collect data inside ReadAction (background-safe)
        final List<Object[]> rows = new java.util.ArrayList<>();
        final int[] featureCount = {0};
        final int[] totalScenarios = {0};
        final int[] stepDefCount = {0};
        final int[] duplicateCount = {0};

        ReadAction.run(() -> {
            List<VirtualFile> featureFiles = CucumberUtils.findFeatureFiles(project);
            StepRegistryService registry = StepRegistryService.getInstance(project);
            registry.refresh();

            featureCount[0] = featureFiles.size();
            stepDefCount[0] = registry.getStepCount();
            duplicateCount[0] = registry.getDuplicates().size();

            for (VirtualFile file : featureFiles) {
                try {
                    String content = new String(file.contentsToByteArray(), "UTF-8");
                    int scenarios = countOccurrences(content, "Scenario:");
                    scenarios += countOccurrences(content, "Scenario Outline:");
                    int steps = countSteps(content);

                    totalScenarios[0] += scenarios;

                    String status = determineStatus(content, registry);
                    rows.add(new Object[]{file.getName(), scenarios, steps, status});
                } catch (Exception ignored) {}
            }
        });

        // Update UI on the EDT
        javax.swing.SwingUtilities.invokeLater(() -> {
            featureTableModel.setRowCount(0);
            for (Object[] row : rows) {
                featureTableModel.addRow(row);
            }
            featureCountLabel.setText(String.valueOf(featureCount[0]));
            scenarioCountLabel.setText(String.valueOf(totalScenarios[0]));
            stepDefCountLabel.setText(String.valueOf(stepDefCount[0]));
            duplicateCountLabel.setText(String.valueOf(duplicateCount[0]));
        });
    }

    private String determineStatus(String content, StepRegistryService registry) {
        var steps = com.cucumberforge.plugin.util.GherkinParser.extractSteps(content);
        if (steps.isEmpty()) return "❌ Empty";

        long defined = steps.stream()
                .filter(s -> !registry.findMatchingDefinitions(s.getText()).isEmpty())
                .count();

        if (defined == steps.size()) return "✅ Complete";
        if (defined > 0) return "⚠️ Partial";
        return "❌ No Defs";
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }

    private int countSteps(String content) {
        return com.cucumberforge.plugin.util.GherkinParser.extractSteps(content).size();
    }

    private void openFeatureFile(String fileName) {
        List<VirtualFile> files = CucumberUtils.findFeatureFiles(project);
        for (VirtualFile f : files) {
            if (f.getName().equals(fileName)) {
                FileEditorManager.getInstance(project).openFile(f, true);
                return;
            }
        }
    }

    private JBLabel createStatCard(String title, String value, JBColor color) {
        JBLabel label = new JBLabel(value);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 28f));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    private JPanel wrapStatCard(JBLabel valueLabel, JBColor accentColor) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accentColor, 1, true),
                JBUI.Borders.empty(12, 8)
        ));

        // Title
        String title;
        if (valueLabel == featureCountLabel) title = "Feature Files";
        else if (valueLabel == scenarioCountLabel) title = "Scenarios";
        else if (valueLabel == stepDefCountLabel) title = "Step Definitions";
        else title = "Duplicates";

        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 11f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setForeground(JBColor.GRAY);

        card.add(valueLabel, BorderLayout.CENTER);
        card.add(titleLabel, BorderLayout.SOUTH);

        return card;
    }
}
