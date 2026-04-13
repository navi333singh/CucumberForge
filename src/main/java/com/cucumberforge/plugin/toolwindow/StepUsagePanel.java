package com.cucumberforge.plugin.toolwindow;

import com.cucumberforge.plugin.model.StepDefinitionModel;
import com.cucumberforge.plugin.services.StepRegistryService;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * Panel showing all step definitions with search, filtering, and navigation.
 */
public class StepUsagePanel {

    private final Project project;
    private final JPanel mainPanel;

    private JBTable stepTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private SearchTextField searchField;

    public StepUsagePanel(Project project) {
        this.project = project;
        this.mainPanel = new JPanel(new BorderLayout());
        buildUI();
        refreshData();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    private void buildUI() {
        // --- Search bar ---
        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.setBorder(JBUI.Borders.empty(8, 16));

        searchField = new SearchTextField();
        searchField.addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override
            protected void textChanged(javax.swing.event.DocumentEvent e) {
                filterTable(searchField.getText());
            }
        });
        topPanel.add(searchField, BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshData());
        topPanel.add(refreshBtn, BorderLayout.EAST);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // --- Step table ---
        tableModel = new DefaultTableModel(
                new String[]{"Annotation", "Pattern", "Method", "Class", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        stepTable = new JBTable(tableModel);
        stepTable.setRowHeight(26);
        stepTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        stepTable.getColumnModel().getColumn(1).setPreferredWidth(350);
        stepTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        stepTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        stepTable.getColumnModel().getColumn(4).setPreferredWidth(80);

        sorter = new TableRowSorter<>(tableModel);
        stepTable.setRowSorter(sorter);

        // Double-click to navigate to step definition
        stepTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelected();
                }
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(stepTable);
        scrollPane.setBorder(JBUI.Borders.empty(0, 16, 12, 16));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // --- Summary panel ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.setBorder(JBUI.Borders.empty(4, 16));
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    public void refreshData() {
        StepRegistryService registry = StepRegistryService.getInstance(project);
        registry.refresh();

        tableModel.setRowCount(0);

        List<StepDefinitionModel> allDefs = registry.getAllStepDefinitions();
        Map<String, List<StepDefinitionModel>> duplicates = registry.getDuplicates();

        for (StepDefinitionModel def : allDefs) {
            String status;
            if (duplicates.containsKey(def.getPattern())) {
                status = "⚠️ Duplicate";
            } else {
                status = "✅ OK";
            }

            tableModel.addRow(new Object[]{
                    def.getAnnotation(),
                    def.getPattern(),
                    def.getMethodName(),
                    def.getClassName(),
                    status
            });
        }
    }

    private void filterTable(String query) {
        if (query == null || query.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            try {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + query));
            } catch (PatternSyntaxException ex) {
                sorter.setRowFilter(null);
            }
        }
    }

    private void navigateToSelected() {
        int viewRow = stepTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = stepTable.convertRowIndexToModel(viewRow);

        StepRegistryService registry = StepRegistryService.getInstance(project);
        List<StepDefinitionModel> defs = registry.getAllStepDefinitions();
        if (modelRow >= defs.size()) return;

        StepDefinitionModel def = defs.get(modelRow);
        String filePath = def.getFilePath();
        int line = def.getLineNumber();

        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (file != null) {
            FileEditorManager.getInstance(project)
                    .openTextEditor(new OpenFileDescriptor(project, file, line - 1, 0), true);
        }
    }
}
