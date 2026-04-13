package com.cucumberforge.plugin.editor;

import com.cucumberforge.plugin.model.StepDefinitionModel;
import com.cucumberforge.plugin.services.StepRegistryService;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Completion contributor for .feature files.
 * Suggests existing step definitions as the user types.
 */
public class StepCompletionContributor extends CompletionContributor {

    public StepCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new StepCompletionProvider());
    }

    private static class StepCompletionProvider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                       @NotNull ProcessingContext context,
                                       @NotNull CompletionResultSet result) {
            PsiFile file = parameters.getOriginalFile();
            VirtualFile vf = file.getVirtualFile();
            if (vf == null || !"feature".equals(vf.getExtension())) return;

            Project project = parameters.getEditor().getProject();
            if (project == null) return;

            // Get current line text to determine if we're in a step context
            int offset = parameters.getOffset();
            String text = parameters.getEditor().getDocument().getText();
            int lineStart = text.lastIndexOf('\n', offset - 1) + 1;
            String linePrefix = text.substring(lineStart, offset).trim();

            // Only suggest if we're after a step keyword
            boolean isStepContext = linePrefix.startsWith("Given ")
                    || linePrefix.startsWith("When ")
                    || linePrefix.startsWith("Then ")
                    || linePrefix.startsWith("And ")
                    || linePrefix.startsWith("But ");

            if (!isStepContext) return;

            // Extract what the user has typed after the keyword
            String typed = "";
            String[] parts = linePrefix.split("\\s+", 2);
            if (parts.length > 1) {
                typed = parts[1];
            }

            StepRegistryService registry = StepRegistryService.getInstance(project);
            List<StepDefinitionModel> allDefs = registry.getAllStepDefinitions();

            Set<String> seenPatterns = new HashSet<>();
            for (StepDefinitionModel def : allDefs) {
                String pattern = def.getPattern();
                if (seenPatterns.contains(pattern)) continue;
                seenPatterns.add(pattern);

                // Convert Cucumber expression placeholders to readable text
                String displayText = pattern
                        .replace("{string}", "\"...\"")
                        .replace("{int}", "N")
                        .replace("{float}", "N.N")
                        .replace("{word}", "word");

                LookupElementBuilder element = LookupElementBuilder.create(displayText)
                        .withTypeText(def.getAnnotation() + " • " + def.getClassName())
                        .withBoldness(true)
                        .withInsertHandler((ctx, item) -> {
                            // Replace with the pattern, keeping placeholders readable
                            // The user can then fill in actual values
                        });

                result.addElement(PrioritizedLookupElement.withPriority(element, 100));
            }

            // Also suggest common step patterns
            addCommonSuggestions(result, linePrefix);
        }

        private void addCommonSuggestions(CompletionResultSet result, String linePrefix) {
            String keyword = linePrefix.split("\\s+")[0];

            String[][] suggestions;
            switch (keyword) {
                case "Given":
                    suggestions = new String[][]{
                            {"the API is available", "Common setup step"},
                            {"I am authenticated as \"{user}\"", "Auth setup"},
                            {"the database contains the following:", "DB setup with table"},
                            {"a {resource} exists with ID \"{id}\"", "Resource exists"},
                    };
                    break;
                case "When":
                    suggestions = new String[][]{
                            {"I send a GET request to \"{endpoint}\"", "HTTP GET"},
                            {"I send a POST request to \"{endpoint}\"", "HTTP POST"},
                            {"I send a PUT request to \"{endpoint}\"", "HTTP PUT"},
                            {"I send a DELETE request to \"{endpoint}\"", "HTTP DELETE"},
                    };
                    break;
                case "Then":
                    suggestions = new String[][]{
                            {"the response status should be {code}", "Status assertion"},
                            {"the response should contain \"{text}\"", "Body assertion"},
                            {"the response should have {count} items", "Count assertion"},
                    };
                    break;
                default:
                    return;
            }

            for (String[] s : suggestions) {
                LookupElementBuilder element = LookupElementBuilder.create(s[0])
                        .withTypeText(s[1])
                        .withItemTextItalic(true);
                result.addElement(PrioritizedLookupElement.withPriority(element, 50));
            }
        }
    }
}
