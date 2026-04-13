package com.cucumberforge.plugin;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class CucumberForgeBundle extends DynamicBundle {

    @NonNls
    private static final String BUNDLE = "messages.CucumberForgeBundle";
    private static final CucumberForgeBundle INSTANCE = new CucumberForgeBundle();

    private CucumberForgeBundle() {
        super(BUNDLE);
    }

    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }
}
