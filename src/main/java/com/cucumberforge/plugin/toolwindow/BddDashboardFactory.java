package com.cucumberforge.plugin.toolwindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory that creates the CucumberForge BDD Dashboard tool window.
 */
public class BddDashboardFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        BddDashboardPanel dashboardPanel = new BddDashboardPanel(project);
        Content content = ContentFactory.getInstance().createContent(
                dashboardPanel.getPanel(), "Dashboard", false);
        toolWindow.getContentManager().addContent(content);

        StepUsagePanel stepPanel = new StepUsagePanel(project);
        Content stepContent = ContentFactory.getInstance().createContent(
                stepPanel.getPanel(), "Step Registry", false);
        toolWindow.getContentManager().addContent(stepContent);
    }
}
