package com.cucumberforge.plugin.editor;

import com.cucumberforge.plugin.services.AiService;
import com.cucumberforge.plugin.services.StepDefinitionService;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI-powered completion provider for .feature files.
 * Provides intelligent step suggestions based on the current context using AI.
 */
public class AiCompletionProvider extends CompletionContributor {

    public AiCompletionProvider() {
        extend(CompletionType.SMART,
                PlatformPatterns.psiElement(),
                new AiStepCompletionProvider());
    }

    private static class AiStepCompletionProvider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                       @NotNull ProcessingContext context,
                                       @NotNull CompletionResultSet result) {
            PsiFile file = parameters.getOriginalFile();
            VirtualFile vf = file.getVirtualFile();
            if (vf == null || !"feature".equals(vf.getExtension())) return;

            Project project = parameters.getEditor().getProject();
            if (project == null) return;

            // Get context around the cursor
            int offset = parameters.getOffset();
            String text = parameters.getEditor().getDocument().getText();
            int lineStart = text.lastIndexOf('\n', offset - 1) + 1;
            String linePrefix = text.substring(lineStart, offset).trim();

            boolean isStepContext = linePrefix.startsWith("Given ")
                    || linePrefix.startsWith("When ")
                    || linePrefix.startsWith("Then ")
                    || linePrefix.startsWith("And ")
                    || linePrefix.startsWith("But ");

            if (!isStepContext) return;

            // Get surrounding context (previous lines for scenario context)
            int contextStart = Math.max(0, text.lastIndexOf("Scenario", offset));
            String scenarioContext = text.substring(contextStart, offset);

            // Get existing steps for reference
            StepDefinitionService stepService = StepDefinitionService.getInstance(project);
            String existingSteps = stepService.getExistingStepsAsContext();

            // Call AI for suggestions (with timeout)
            AiService aiService = AiService.getInstance(project);
            try {
                List<String> suggestions = aiService.suggestStepCompletions(
                        linePrefix, scenarioContext, existingSteps)
                        .get(5, TimeUnit.SECONDS);

                for (int i = 0; i < suggestions.size(); i++) {
                    String suggestion = suggestions.get(i);
                    LookupElementBuilder element = LookupElementBuilder.create(suggestion)
                            .withTypeText("AI Suggestion")
                            .withBoldness(true)
                            .withIcon(null); // Could add AI icon here

                    result.addElement(PrioritizedLookupElement.withPriority(element, 200 - i));
                }
            } catch (Exception ignored) {
                // AI completion failed silently — basic completion still works
            }
        }
    }
}
