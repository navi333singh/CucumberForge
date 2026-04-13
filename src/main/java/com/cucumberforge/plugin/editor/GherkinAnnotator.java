package com.cucumberforge.plugin.editor;

import com.cucumberforge.plugin.model.StepDefinitionModel;
import com.cucumberforge.plugin.services.StepRegistryService;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Annotator for .feature files that shows inline indicators for step definition status:
 * - Green: step has a matching definition
 * - Red: step is undefined
 * - Orange: step matches multiple definitions (ambiguous)
 */
public class GherkinAnnotator implements Annotator {

    private static final Pattern STEP_LINE_PATTERN =
            Pattern.compile("^\\s*(Given|When|Then|And|But)\\s+(.+)$");

    private static final TextAttributesKey DEFINED_STEP = TextAttributesKey.createTextAttributesKey(
            "CUCUMBERFORGE_DEFINED_STEP", DefaultLanguageHighlighterColors.STRING);

    private static final TextAttributesKey UNDEFINED_STEP = TextAttributesKey.createTextAttributesKey(
            "CUCUMBERFORGE_UNDEFINED_STEP", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Only process feature files
        PsiFile file = element.getContainingFile();
        if (file == null) return;
        VirtualFile vf = file.getVirtualFile();
        if (vf == null || !"feature".equals(vf.getExtension())) return;

        // Only annotate on the file-level PsiElement (avoid per-character annotations)
        if (element != file) return;

        Project project = element.getProject();
        StepRegistryService registry = StepRegistryService.getInstance(project);

        String text = file.getText();
        String[] lines = text.split("\\r?\\n");
        int offset = 0;

        for (String line : lines) {
            Matcher m = STEP_LINE_PATTERN.matcher(line);
            if (m.matches()) {
                String stepText = m.group(2).trim();
                int stepStart = offset + m.start(2);
                int stepEnd = offset + m.end();

                TextRange range = new TextRange(stepStart, Math.min(stepEnd, text.length()));

                List<StepDefinitionModel> matches = registry.findMatchingDefinitions(stepText);

                if (matches.isEmpty()) {
                    holder.newAnnotation(HighlightSeverity.WARNING,
                                    "❌ Undefined step: no matching step definition found")
                            .range(range)
                            .textAttributes(UNDEFINED_STEP)
                            .create();
                } else if (matches.size() > 1) {
                    holder.newAnnotation(HighlightSeverity.WARNING,
                                    "⚠️ Ambiguous step: " + matches.size() + " matching definitions")
                            .range(range)
                            .create();
                } else {
                    StepDefinitionModel def = matches.get(0);
                    holder.newAnnotation(HighlightSeverity.INFORMATION,
                                    "✅ Defined in " + def.getClassName() + "." + def.getMethodName() + "()")
                            .range(range)
                            .textAttributes(DEFINED_STEP)
                            .create();
                }
            }
            offset += line.length() + 1; // +1 for newline
        }
    }
}
