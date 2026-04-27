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
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Main BDD Dashboard panel showing overview statistics and feature file tree.
 * Modern IntelliJ-native look with rounded stat cards and styled table.
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
        mainPanel.setBackground(UIUtil.getPanelBackground());
        buildUI();
        refreshData();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    private void buildUI() {
        // --- Header ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(JBUI.Borders.empty(12, 16, 4, 16));
        headerPanel.setOpaque(false);

        JBLabel titleLabel = new JBLabel("BDD Test Overview");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JButton refreshButton = createStyledButton("Refresh");
        refreshButton.addActionListener(e -> refreshData());
        headerPanel.add(refreshButton, BorderLayout.EAST);

        // --- Stats panel ---
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        statsPanel.setBorder(JBUI.Borders.empty(8, 16, 12, 16));
        statsPanel.setOpaque(false);

        featureCountLabel = new JBLabel("0");
        scenarioCountLabel = new JBLabel("0");
        stepDefCountLabel = new JBLabel("0");
        duplicateCountLabel = new JBLabel("0");

        statsPanel.add(createStatCard("Features", featureCountLabel,
                new JBColor(0x4CAF50, 0x66BB6A), new JBColor(0xE8F5E9, 0x1B3A1B)));
        statsPanel.add(createStatCard("Scenarios", scenarioCountLabel,
                new JBColor(0x2196F3, 0x42A5F5), new JBColor(0xE3F2FD, 0x102840)));
        statsPanel.add(createStatCard("Step Defs", stepDefCountLabel,
                new JBColor(0xFF9800, 0xFFA726), new JBColor(0xFFF3E0, 0x3D2800)));
        statsPanel.add(createStatCard("Duplicates", duplicateCountLabel,
                new JBColor(0xF44336, 0xEF5350), new JBColor(0xFFEBEE, 0x3D1010)));

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(headerPanel, BorderLayout.NORTH);
        topSection.add(statsPanel, BorderLayout.CENTER);
        mainPanel.add(topSection, BorderLayout.NORTH);

        // --- Feature file table ---
        JPanel tableHeader = new JPanel(new BorderLayout());
        tableHeader.setBorder(JBUI.Borders.empty(4, 16, 4, 16));
        tableHeader.setOpaque(false);
        JBLabel tableTitle = new JBLabel("Feature Files");
        tableTitle.setFont(tableTitle.getFont().deriveFont(Font.BOLD, 13f));
        tableTitle.setForeground(UIUtil.getLabelDisabledForeground());
        tableHeader.add(tableTitle, BorderLayout.WEST);

        featureTableModel = new DefaultTableModel(
                new String[]{"Feature File", "Scenarios", "Steps", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        featureTable = new JBTable(featureTableModel);
        featureTable.setRowHeight(32);
        featureTable.setShowGrid(false);
        featureTable.setIntercellSpacing(new Dimension(0, 1));
        featureTable.getTableHeader().setFont(
                featureTable.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        featureTable.getColumnModel().getColumn(0).setPreferredWidth(320);
        featureTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        featureTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        featureTable.getColumnModel().getColumn(3).setPreferredWidth(110);

        // Center numeric columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        featureTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        featureTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);

        // Status column renderer with colored badges
        featureTable.getColumnModel().getColumn(3).setCellRenderer(new StatusBadgeRenderer());

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

        JPanel centerSection = new JPanel(new BorderLayout());
        centerSection.setOpaque(false);
        centerSection.add(tableHeader, BorderLayout.NORTH);
        centerSection.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(centerSection, BorderLayout.CENTER);

        // --- Bottom tips ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bottomPanel.setBorder(JBUI.Borders.empty(0, 16, 8, 16));
        bottomPanel.setOpaque(false);
        JBLabel tipLabel = new JBLabel("Double-click a feature to open it. Use Tools > CucumberForge for more actions.");
        tipLabel.setFont(tipLabel.getFont().deriveFont(Font.ITALIC, 11f));
        tipLabel.setForeground(UIUtil.getLabelDisabledForeground());
        bottomPanel.add(tipLabel);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    public void refreshData() {
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

        SwingUtilities.invokeLater(() -> {
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

    // =================== UI Helpers ===================

    private JPanel createStatCard(String title, JBLabel valueLabel, JBColor accentColor, JBColor bgColor) {
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 26f));
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        valueLabel.setForeground(accentColor);

        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 11f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setForeground(UIUtil.getLabelDisabledForeground());

        JPanel card = new JPanel(new BorderLayout(0, 2)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(accentColor);
                g2.fillRoundRect(0, getHeight() - 3, getWidth(), 3, 2, 2);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(JBUI.Borders.empty(10, 8, 8, 8));
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(titleLabel, BorderLayout.SOUTH);
        return card;
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.putClientProperty("JButton.buttonType", "roundRect");
        return button;
    }

    /**
     * Custom renderer for the Status column showing colored badges.
     */
    private static class StatusBadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            panel.setOpaque(isSelected);
            if (isSelected) {
                panel.setBackground(table.getSelectionBackground());
            }

            String status = String.valueOf(value);
            JLabel badge = new JLabel(status);
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11f));
            badge.setOpaque(true);
            badge.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

            if (status.contains("Complete")) {
                badge.setForeground(new JBColor(0x2E7D32, 0x81C784));
                badge.setBackground(new JBColor(0xE8F5E9, 0x1B3A1B));
            } else if (status.contains("Partial")) {
                badge.setForeground(new JBColor(0xE65100, 0xFFB74D));
                badge.setBackground(new JBColor(0xFFF3E0, 0x3D2800));
            } else {
                badge.setForeground(new JBColor(0xC62828, 0xE57373));
                badge.setBackground(new JBColor(0xFFEBEE, 0x3D1010));
            }

            panel.add(badge);
            return panel;
        }
    }

    // =================== Data Helpers ===================

    private String determineStatus(String content, StepRegistryService registry) {
        var steps = com.cucumberforge.plugin.util.GherkinParser.extractSteps(content);
        if (steps.isEmpty()) return "No Steps";

        long defined = steps.stream()
                .filter(s -> !registry.findMatchingDefinitions(s.getText()).isEmpty())
                .count();

        if (defined == steps.size()) return "Complete";
        if (defined > 0) return "Partial (" + defined + "/" + steps.size() + ")";
        return "No Defs";
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
}
