package com.cucumberforge.plugin.services;

import com.google.gson.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for AI-assisted test generation.
 * Supports OpenAI API and GitHub Copilot.
 */
@Service(Service.Level.PROJECT)
public final class AiService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Project project;
    private final OkHttpClient httpClient;

    public AiService(@NotNull Project project) {
        this.project = project;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static AiService getInstance(@NotNull Project project) {
        return project.getService(AiService.class);
    }

    /**
     * Generate Cucumber feature + step definitions for a given class context.
     *
     * @param classContext     The analyzed class information
     * @param existingFeatures Existing feature files as reference
     * @param existingSteps    Existing step definitions as reference
     * @return CompletableFuture containing the generated test code
     */
    public CompletableFuture<GenerationResult> generateTests(String classContext,
                                                              String existingFeatures,
                                                              String existingSteps) {
        SettingsService settings = SettingsService.getInstance(project);
        String prompt = buildGenerationPrompt(classContext, existingFeatures, existingSteps);

        if ("COPILOT".equals(settings.getAiProvider())) {
            return generateWithCopilot(prompt);
        } else {
            return generateWithOpenAi(prompt, settings);
        }
    }

    /**
     * Generate AI completion for a partial Gherkin step.
     *
     * @param partialStep     The partially written step text
     * @param scenarioContext The current scenario context
     * @param existingSteps   Existing steps for reference
     * @return CompletableFuture containing suggested completions
     */
    public CompletableFuture<List<String>> suggestStepCompletions(String partialStep,
                                                                   String scenarioContext,
                                                                   String existingSteps) {
        String prompt = buildCompletionPrompt(partialStep, scenarioContext, existingSteps);
        SettingsService settings = SettingsService.getInstance(project);

        CompletableFuture<GenerationResult> future;
        if ("COPILOT".equals(settings.getAiProvider())) {
            future = generateWithCopilot(prompt);
        } else {
            future = generateWithOpenAi(prompt, settings);
        }

        return future.thenApply(result -> {
            List<String> suggestions = new ArrayList<>();
            if (result.getContent() != null) {
                String[] lines = result.getContent().split("\\r?\\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("//")) {
                        suggestions.add(trimmed);
                    }
                }
            }
            return suggestions;
        });
    }

    /**
     * Refine/improve an existing scenario.
     */
    public CompletableFuture<String> refineScenario(String scenario, String existingSteps) {
        String prompt = buildRefinePrompt(scenario, existingSteps);
        SettingsService settings = SettingsService.getInstance(project);

        CompletableFuture<GenerationResult> future;
        if ("COPILOT".equals(settings.getAiProvider())) {
            future = generateWithCopilot(prompt);
        } else {
            future = generateWithOpenAi(prompt, settings);
        }

        return future.thenApply(GenerationResult::getContent);
    }

    // =================== OpenAI Implementation ===================

    private CompletableFuture<GenerationResult> generateWithOpenAi(String prompt, SettingsService settings) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = settings.getOpenAiApiKey();
                if (apiKey == null || apiKey.isEmpty()) {
                    return new GenerationResult(null,
                            "OpenAI API key not configured. Go to Settings > Tools > CucumberForge to set it.");
                }

                String model = settings.getOpenAiModel();
                String baseUrl = settings.getState().openAiBaseUrl;

                JsonObject messageSystem = new JsonObject();
                messageSystem.addProperty("role", "system");
                messageSystem.addProperty("content", SYSTEM_PROMPT);

                JsonObject messageUser = new JsonObject();
                messageUser.addProperty("role", "user");
                messageUser.addProperty("content", prompt);

                JsonArray messages = new JsonArray();
                messages.add(messageSystem);
                messages.add(messageUser);

                JsonObject body = new JsonObject();
                body.addProperty("model", model);
                body.add("messages", messages);
                body.addProperty("temperature", 0.3);
                body.addProperty("max_tokens", 4096);

                RequestBody requestBody = RequestBody.create(body.toString(), JSON);
                Request request = new Request.Builder()
                        .url(baseUrl + "/chat/completions")
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        return new GenerationResult(null, "OpenAI API error (" + response.code() + "): " + errorBody);
                    }
                    String responseBody = response.body().string();
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    String content = json.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();
                    return new GenerationResult(content, null);
                }
            } catch (Exception e) {
                return new GenerationResult(null, "Error: " + e.getMessage());
            }
        });
    }

    // =================== GitHub Copilot Implementation ===================

    private CompletableFuture<GenerationResult> generateWithCopilot(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // GitHub Copilot chat API via the IDE's built-in Copilot plugin
                // We use the GitHub Copilot API endpoint with the IDE's cached auth token
                String token = getCopilotToken();
                if (token == null || token.isEmpty()) {
                    return new GenerationResult(null,
                            "GitHub Copilot not authenticated. Please sign in to GitHub Copilot in your IDE first.");
                }

                // Use the Copilot Chat completions endpoint
                JsonObject messageSystem = new JsonObject();
                messageSystem.addProperty("role", "system");
                messageSystem.addProperty("content", SYSTEM_PROMPT);

                JsonObject messageUser = new JsonObject();
                messageUser.addProperty("role", "user");
                messageUser.addProperty("content", prompt);

                JsonArray messages = new JsonArray();
                messages.add(messageSystem);
                messages.add(messageUser);

                JsonObject body = new JsonObject();
                body.addProperty("model", "gpt-4o");
                body.add("messages", messages);
                body.addProperty("temperature", 0.3);
                body.addProperty("max_tokens", 4096);

                RequestBody requestBody = RequestBody.create(body.toString(), JSON);
                Request request = new Request.Builder()
                        .url("https://api.githubcopilot.com/chat/completions")
                        .addHeader("Authorization", "Bearer " + token)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Editor-Version", "JetBrains-IC/2024.3")
                        .addHeader("Copilot-Integration-Id", "vscode-chat")
                        .post(requestBody)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        return new GenerationResult(null,
                                "GitHub Copilot error (" + response.code() + "): " + errorBody);
                    }
                    String responseBody = response.body().string();
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    String content = json.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();
                    return new GenerationResult(content, null);
                }
            } catch (Exception e) {
                return new GenerationResult(null, "Copilot error: " + e.getMessage());
            }
        });
    }

    /**
     * Attempt to retrieve the GitHub Copilot OAuth token from the IDE's config.
     */
    private String getCopilotToken() {
        try {
            // Try reading from the standard Copilot token file locations
            String userHome = System.getProperty("user.home");
            java.nio.file.Path[] tokenPaths = {
                    java.nio.file.Paths.get(userHome, ".config", "github-copilot", "hosts.json"),
                    java.nio.file.Paths.get(userHome, ".config", "github-copilot", "apps.json"),
                    java.nio.file.Paths.get(System.getenv("LOCALAPPDATA") != null
                            ? System.getenv("LOCALAPPDATA") : userHome, "github-copilot", "hosts.json")
            };

            for (java.nio.file.Path path : tokenPaths) {
                if (java.nio.file.Files.exists(path)) {
                    String content = java.nio.file.Files.readString(path);
                    JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                    // Extract oauth_token from the first entry
                    for (String key : json.keySet()) {
                        JsonObject entry = json.getAsJsonObject(key);
                        if (entry != null && entry.has("oauth_token")) {
                            return entry.get("oauth_token").getAsString();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Token not available
        }
        return null;
    }

    // =================== Prompt Engineering ===================

    private static final String SYSTEM_PROMPT =
            "You are an expert BDD test engineer specializing in Cucumber with Java Spring Boot. " +
            "You write clean, maintainable, and comprehensive Gherkin scenarios and Java step definitions. " +
            "Follow these rules:\n" +
            "1. Use Cucumber expressions (not regex) for step patterns\n" +
            "2. Use Given-When-Then structure strictly\n" +
            "3. Keep scenarios focused on one behavior\n" +
            "4. Use Scenario Outline with Examples for parameterized tests\n" +
            "5. Include both happy path and error scenarios\n" +
            "6. Use meaningful step descriptions that are reusable\n" +
            "7. Generate Java step definitions with proper Spring annotations\n" +
            "8. Include TODO comments where implementation details are needed\n" +
            "9. Match the style and conventions of existing tests when provided\n" +
            "10. ALWAYS respond with code blocks: one ```gherkin block for the .feature file " +
            "and one ```java block for step definitions.";

    private String buildGenerationPrompt(String classContext, String existingFeatures, String existingSteps) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate comprehensive Cucumber BDD tests for the following class.\n\n");
        sb.append(classContext).append("\n\n");

        if (existingFeatures != null && !existingFeatures.isEmpty()) {
            sb.append("=== Existing Feature Files (match this style) ===\n");
            sb.append(existingFeatures).append("\n\n");
        }

        if (existingSteps != null && !existingSteps.isEmpty()) {
            sb.append("=== Existing Step Definitions (reuse these where possible) ===\n");
            sb.append(existingSteps).append("\n\n");
        }

        SettingsService settings = SettingsService.getInstance(project);
        if (!settings.getState().customPromptPrefix.isEmpty()) {
            sb.append("Additional instructions: ").append(settings.getState().customPromptPrefix).append("\n\n");
        }

        sb.append("Generate:\n");
        sb.append("1. A complete .feature file with multiple scenarios (happy path + error cases)\n");
        sb.append("2. The corresponding Java step definitions class\n");
        sb.append("Reuse existing step definitions where applicable.\n");

        return sb.toString();
    }

    private String buildCompletionPrompt(String partialStep, String scenarioContext, String existingSteps) {
        return "Complete the following Gherkin step. Suggest 3-5 possible completions.\n\n" +
                "Current scenario context:\n" + scenarioContext + "\n\n" +
                "Partial step: " + partialStep + "\n\n" +
                "Available steps in project:\n" + existingSteps + "\n\n" +
                "Return only the completed step text, one per line. No explanations.";
    }

    private String buildRefinePrompt(String scenario, String existingSteps) {
        return "Improve the following Cucumber scenario. Make it more comprehensive, " +
                "add edge cases, and ensure good step reusability.\n\n" +
                "Current scenario:\n" + scenario + "\n\n" +
                "Available steps:\n" + existingSteps + "\n\n" +
                "Return the improved scenario in a ```gherkin code block.";
    }

    // =================== Result class ===================

    public static class GenerationResult {
        private final String content;
        private final String error;

        public GenerationResult(String content, String error) {
            this.content = content;
            this.error = error;
        }

        public String getContent() { return content; }
        public String getError() { return error; }
        public boolean isSuccess() { return error == null && content != null; }

        /**
         * Extract the feature file content from the AI response.
         */
        public String extractFeatureContent() {
            if (content == null) return "";
            return extractCodeBlock(content, "gherkin", "feature");
        }

        /**
         * Extract the Java step definitions from the AI response.
         */
        public String extractJavaContent() {
            if (content == null) return "";
            return extractCodeBlock(content, "java");
        }

        private String extractCodeBlock(String text, String... languages) {
            for (String lang : languages) {
                String marker = "```" + lang;
                int start = text.indexOf(marker);
                if (start >= 0) {
                    start += marker.length();
                    // Skip to next line
                    int lineEnd = text.indexOf('\n', start);
                    if (lineEnd >= 0) start = lineEnd + 1;

                    int end = text.indexOf("```", start);
                    if (end >= 0) {
                        return text.substring(start, end).trim();
                    }
                }
            }
            return text; // Return full content if no code block found
        }
    }
}
