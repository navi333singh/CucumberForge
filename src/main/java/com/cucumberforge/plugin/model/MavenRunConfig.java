package com.cucumberforge.plugin.model;

/**
 * Represents a Maven run configuration that can be saved
 * as an IntelliJ run config or executed from terminal.
 */
public class MavenRunConfig {

    private String name;
    private String command;
    private String description;
    private RunConfigType type;
    private String workingDirectory;

    public MavenRunConfig() {}

    public MavenRunConfig(String name, String command, String description, RunConfigType type) {
        this.name = name;
        this.command = command;
        this.description = description;
        this.type = type;
        this.workingDirectory = "";
    }

    // --- Getters & Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public RunConfigType getType() { return type; }
    public void setType(RunConfigType type) { this.type = type; }

    public String getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }

    /**
     * Build the full Maven command line string.
     */
    public String toCommandLine() {
        return "mvn " + command;
    }

    /**
     * Generate IntelliJ run configuration XML content.
     */
    public String toIntelliJRunConfigXml(String projectBasePath) {
        String wd = workingDirectory.isEmpty() ? projectBasePath : workingDirectory;
        return "<component name=\"ProjectRunConfigurationManager\">\n" +
                "  <configuration default=\"false\" name=\"" + xmlEscape(name) + "\" type=\"MavenRunConfiguration\" factoryName=\"Maven\">\n" +
                "    <MavenSettings>\n" +
                "      <option name=\"myGeneralSettings\" />\n" +
                "      <option name=\"myRunnerSettings\" />\n" +
                "      <option name=\"myRunnerParameters\">\n" +
                "        <MavenRunnerParameters>\n" +
                "          <option name=\"cmdOptions\" value=\"\" />\n" +
                "          <option name=\"goals\">\n" +
                buildGoalsList() +
                "          </option>\n" +
                "          <option name=\"profilesMap\">\n" +
                "            <map />\n" +
                "          </option>\n" +
                "          <option name=\"resolveToWorkspace\" value=\"false\" />\n" +
                "          <option name=\"workingDirPath\" value=\"" + xmlEscape(wd) + "\" />\n" +
                "        </MavenRunnerParameters>\n" +
                "      </option>\n" +
                "    </MavenSettings>\n" +
                "    <method v=\"2\" />\n" +
                "  </configuration>\n" +
                "</component>\n";
    }

    private String buildGoalsList() {
        StringBuilder sb = new StringBuilder();
        sb.append("            <list>\n");
        String[] parts = command.split("\\s+");
        for (String part : parts) {
            sb.append("              <option value=\"").append(xmlEscape(part)).append("\" />\n");
        }
        sb.append("            </list>\n");
        return sb.toString();
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // --- Enum ---

    public enum RunConfigType {
        RUN_APP("Run Application"),
        TEST_ALL("Run All Tests"),
        TEST_SINGLE("Run Single Test"),
        TEST_TAG("Run Tests by Tag"),
        CUSTOM("Custom Command");

        private final String display;
        RunConfigType(String display) { this.display = display; }
        public String getDisplay() { return display; }
        @Override public String toString() { return display; }
    }
}
