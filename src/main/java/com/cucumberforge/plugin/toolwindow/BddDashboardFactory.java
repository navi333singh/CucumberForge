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
 * Includes tabs for Dashboard, Step Registry, and Maven Run Configurations.
 */
public class BddDashboardFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Tab 1: Dashboard overview
        BddDashboardPanel dashboardPanel = new BddDashboardPanel(project);
        Content dashboardContent = ContentFactory.getInstance().createContent(
                dashboardPanel.getPanel(), "Dashboard", false);
        toolWindow.getContentManager().addContent(dashboardContent);

        // Tab 2: Step Registry
        StepUsagePanel stepPanel = new StepUsagePanel(project);
        Content stepContent = ContentFactory.getInstance().createContent(
                stepPanel.getPanel(), "Step Registry", false);
        toolWindow.getContentManager().addContent(stepContent);

        // Tab 3: Maven Run Configurations
        MavenRunConfigPanel mavenPanel = new MavenRunConfigPanel(project);
        Content mavenContent = ContentFactory.getInstance().createContent(
                mavenPanel.getPanel(), "Maven Configs", false);
        toolWindow.getContentManager().addContent(mavenContent);
    }
}
