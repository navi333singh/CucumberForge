package com.cucumberforge.plugin.services;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent settings service for CucumberForge plugin.
 * Stores AI configuration and project preferences.
 */
@Service(Service.Level.PROJECT)
@State(name = "CucumberForgeSettings", storages = @Storage("cucumberforge.xml"))
public final class SettingsService implements PersistentStateComponent<SettingsService.State> {

    private State myState = new State();

    public static SettingsService getInstance(@NotNull Project project) {
        return project.getService(SettingsService.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, myState);
    }

    // Convenience accessors
    public String getAiProvider() { return myState.aiProvider; }
    public void setAiProvider(String value) { myState.aiProvider = value; }

    public String getOpenAiApiKey() { return myState.openAiApiKey; }
    public void setOpenAiApiKey(String value) { myState.openAiApiKey = value; }

    public String getOpenAiModel() { return myState.openAiModel; }
    public void setOpenAiModel(String value) { myState.openAiModel = value; }

    public String getBasePackage() { return myState.basePackage; }
    public void setBasePackage(String value) { myState.basePackage = value; }

    public boolean isUseCopilot() { return "COPILOT".equals(myState.aiProvider); }

    /**
     * State class holding all persisted fields.
     */
    public static class State {
        public String aiProvider = "OPENAI";
        public String openAiApiKey = "";
        public String openAiModel = "gpt-4o";
        public String openAiBaseUrl = "https://api.openai.com/v1";
        public String basePackage = "com.example.test";
        public String customPromptPrefix = "";
    }
}
