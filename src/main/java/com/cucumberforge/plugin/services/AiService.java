package com.cucumberforge.plugin.services;

import com.google.gson.*;
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
 * Includes intelligent prompt engineering that analyzes existing test patterns.
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
                String token = getCopilotToken();
                if (token == null || token.isEmpty()) {
                    return new GenerationResult(null,
                            "GitHub Copilot not authenticated. Please sign in to GitHub Copilot in your IDE first.");
                }

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

    private String getCopilotToken() {
        try {
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
                    for (String key : json.keySet()) {
                        JsonObject entry = json.getAsJsonObject(key);
                        if (entry != null && entry.has("oauth_token")) {
                            return entry.get("oauth_token").getAsString();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // =================== Prompt Engineering ===================

    private static final String SYSTEM_PROMPT =
            "You are an expert BDD test engineer specializing in Cucumber with Java Spring Boot. " +
            "You write clean, maintainable, and comprehensive Gherkin scenarios and Java step definitions.\n\n" +
            "CRITICAL RULES:\n" +
            "1. Use Cucumber expressions (not regex) for step patterns\n" +
            "2. Use Given-When-Then structure strictly\n" +
            "3. Keep scenarios focused on one behavior\n" +
            "4. Use Scenario Outline with Examples for parameterized tests\n" +
            "5. Include both happy path and error scenarios\n" +
            "6. Use meaningful step descriptions that are reusable\n" +
            "7. Generate Java step definitions with proper Spring annotations\n" +
            "8. Include TODO comments where implementation details are needed\n" +
            "9. ALWAYS respond with code blocks: one ```gherkin block for the .feature file " +
            "and one ```java block for step definitions.\n\n" +
            "CONSISTENCY RULES (VERY IMPORTANT):\n" +
            "10. When existing test code is provided, you MUST follow the EXACT same patterns:\n" +
            "    - If existing tests use WebTestClient, your new tests MUST also use WebTestClient\n" +
            "    - If existing tests use MockMvc, your new tests MUST also use MockMvc\n" +
            "    - If existing tests use RestAssured, your new tests MUST also use RestAssured\n" +
            "    - Match the same assertion library (AssertJ, Hamcrest, JUnit assertions)\n" +
            "    - Match the same import style and class organization\n" +
            "    - Reuse the same helper methods and base classes that existing tests use\n" +
            "    - Follow the same variable naming conventions\n" +
            "    - Use the same @Autowired / @MockBean patterns\n" +
            "    - If existing tests extend a base class, the new test MUST extend the same base class\n" +
            "11. Match the EXACT coding style: indentation, bracket placement, comment style\n" +
            "12. If existing step definition classes inject services with @Autowired, do the same\n" +
            "13. If existing tests use a shared state/context object, reuse it\n" +
            "14. Reuse existing step definitions where applicable — do NOT create duplicates";

    private String buildGenerationPrompt(String classContext, String existingFeatures, String existingSteps) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate comprehensive Cucumber BDD tests for the following class.\n\n");
        sb.append("=== Target Class ===\n");
        sb.append(classContext).append("\n\n");

        // Analyze existing test patterns
        if (existingSteps != null && !existingSteps.isEmpty()) {
            sb.append("=== EXISTING STEP DEFINITIONS — YOU MUST FOLLOW THIS EXACT PATTERN ===\n\n");

            // Detect HTTP client in use
            String httpClientDetected = detectHttpClient(existingSteps);
            if (httpClientDetected != null) {
                sb.append("*** DETECTED HTTP CLIENT: ").append(httpClientDetected).append(" ***\n");
                sb.append("You MUST use ").append(httpClientDetected).append(" in your generated code.\n");
                sb.append("Do NOT use any other HTTP client library.\n\n");
            }

            // Detect assertion library
            String assertionLib = detectAssertionLibrary(existingSteps);
            if (assertionLib != null) {
                sb.append("*** DETECTED ASSERTION LIBRARY: ").append(assertionLib).append(" ***\n");
                sb.append("You MUST use ").append(assertionLib).append(" for all assertions.\n\n");
            }

            // Detect base class
            String baseClass = detectBaseClass(existingSteps);
            if (baseClass != null) {
                sb.append("*** DETECTED BASE CLASS: ").append(baseClass).append(" ***\n");
                sb.append("Your new step definitions class MUST extend ").append(baseClass).append(".\n\n");
            }

            // Detect dependency injection pattern
            String diPattern = detectDiPattern(existingSteps);
            if (diPattern != null) {
                sb.append("*** DI PATTERN: ").append(diPattern).append(" ***\n\n");
            }

            // Detect shared state / context objects
            String sharedState = detectSharedState(existingSteps);
            if (sharedState != null) {
                sb.append("*** SHARED STATE OBJECTS: ").append(sharedState).append(" ***\n");
                sb.append("Reuse these shared context objects in your step definitions.\n\n");
            }

            sb.append("Existing step definitions (follow this style exactly):\n");
            sb.append(existingSteps).append("\n\n");
        }

        if (existingFeatures != null && !existingFeatures.isEmpty()) {
            sb.append("=== EXISTING FEATURE FILES (match this Gherkin style) ===\n");
            sb.append(existingFeatures).append("\n\n");
        }

        SettingsService settings = SettingsService.getInstance(project);
        if (!settings.getState().customPromptPrefix.isEmpty()) {
            sb.append("=== ADDITIONAL USER INSTRUCTIONS ===\n");
            sb.append(settings.getState().customPromptPrefix).append("\n\n");
        }

        sb.append("=== YOUR TASK ===\n");
        sb.append("Generate:\n");
        sb.append("1. A complete .feature file with multiple scenarios (happy path + error cases + edge cases)\n");
        sb.append("2. The corresponding Java step definitions class that follows the EXACT same patterns ");
        sb.append("as the existing test code shown above\n");
        sb.append("3. Reuse existing step definitions where applicable — do NOT create duplicate steps\n");
        sb.append("4. The generated step definitions must be immediately compilable and consistent ");
        sb.append("with the rest of the test suite\n");

        return sb.toString();
    }

    /**
     * Detect which HTTP client the existing tests use.
     */
    private String detectHttpClient(String code) {
        if (code.contains("WebTestClient") || code.contains("webTestClient")) {
            return "WebTestClient";
        }
        if (code.contains("MockMvc") || code.contains("mockMvc")) {
            return "MockMvc";
        }
        if (code.contains("RestAssured") || code.contains("given().") || code.contains("io.restassured")) {
            return "RestAssured";
        }
        if (code.contains("TestRestTemplate") || code.contains("testRestTemplate")) {
            return "TestRestTemplate";
        }
        return null;
    }

    /**
     * Detect which assertion library the existing tests use.
     */
    private String detectAssertionLibrary(String code) {
        if (code.contains("assertThat(") && code.contains("org.assertj")) {
            return "AssertJ (org.assertj.core.api.Assertions.assertThat)";
        }
        if (code.contains("assertThat(") && code.contains("org.hamcrest")) {
            return "Hamcrest (org.hamcrest.MatcherAssert.assertThat)";
        }
        if (code.contains("assertEquals(") || code.contains("assertTrue(")) {
            return "JUnit Assertions (org.junit.jupiter.api.Assertions)";
        }
        if (code.contains("assertThat(")) {
            return "AssertJ (auto-detected from assertThat usage)";
        }
        return null;
    }

    /**
     * Detect if step definition classes extend a base class.
     */
    private String detectBaseClass(String code) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("class\\s+\\w+\\s+extends\\s+(\\w+)")
                .matcher(code);
        if (m.find()) {
            String base = m.group(1);
            if (!base.equals("Object")) return base;
        }
        return null;
    }

    /**
     * Detect dependency injection patterns.
     */
    private String detectDiPattern(String code) {
        List<String> patterns = new ArrayList<>();
        if (code.contains("@Autowired")) patterns.add("Spring @Autowired injection");
        if (code.contains("@MockBean")) patterns.add("@MockBean for mocking");
        if (code.contains("@SpyBean")) patterns.add("@SpyBean for partial mocks");
        if (code.contains("constructor")) patterns.add("Constructor injection via PicoContainer");
        return patterns.isEmpty() ? null : String.join(", ", patterns);
    }

    /**
     * Detect shared state/context objects (common in Cucumber).
     */
    private String detectSharedState(String code) {
        List<String> stateObjects = new ArrayList<>();
        // Look for common shared state patterns
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(private|protected)\\s+(\\w*(?:Context|State|World|SharedData|TestContext)\\w*)\\s+(\\w+)")
                .matcher(code);
        while (m.find()) {
            stateObjects.add(m.group(2) + " " + m.group(3));
        }
        // Also look for Response/Result fields that are shared across steps
        m = java.util.regex.Pattern
                .compile("(private|protected)\\s+(Response(?:Spec)?|MvcResult|ResponseEntity)\\s+(\\w+)")
                .matcher(code);
        while (m.find()) {
            stateObjects.add(m.group(2) + " " + m.group(3));
        }
        return stateObjects.isEmpty() ? null : String.join(", ", stateObjects);
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

        public String extractFeatureContent() {
            if (content == null) return "";
            return extractCodeBlock(content, "gherkin", "feature");
        }

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
                    int lineEnd = text.indexOf('\n', start);
                    if (lineEnd >= 0) start = lineEnd + 1;

                    int end = text.indexOf("```", start);
                    if (end >= 0) {
                        return text.substring(start, end).trim();
                    }
                }
            }
            return text;
        }
    }
}
