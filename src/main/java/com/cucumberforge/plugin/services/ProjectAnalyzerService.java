package com.cucumberforge.plugin.services;

import com.cucumberforge.plugin.util.CucumberUtils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Analyzes the project structure to extract useful information
 * for AI-assisted test generation (controllers, services, entities, etc.).
 */
@Service(Service.Level.PROJECT)
public final class ProjectAnalyzerService {

    private final Project project;

    public ProjectAnalyzerService(@NotNull Project project) {
        this.project = project;
    }

    public static ProjectAnalyzerService getInstance(@NotNull Project project) {
        return project.getService(ProjectAnalyzerService.class);
    }

    /**
     * Extract class information (methods, fields, annotations) from a PsiJavaFile.
     */
    public String extractClassInfo(PsiJavaFile javaFile) {
        return ReadAction.compute(() -> {
            StringBuilder sb = new StringBuilder();
            for (PsiClass psiClass : javaFile.getClasses()) {
                sb.append(extractClassDetails(psiClass));
            }
            return sb.toString();
        });
    }

    /**
     * Detect if a class is a Spring REST controller.
     */
    public boolean isRestController(PsiClass psiClass) {
        return ReadAction.compute(() -> {
            PsiAnnotation[] annotations = psiClass.getAnnotations();
            for (PsiAnnotation ann : annotations) {
                String name = ann.getQualifiedName();
                if (name != null && (name.endsWith("RestController") || name.endsWith("Controller"))) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Detect if a class is a JPA Entity.
     */
    public boolean isEntity(PsiClass psiClass) {
        return ReadAction.compute(() -> {
            PsiAnnotation[] annotations = psiClass.getAnnotations();
            for (PsiAnnotation ann : annotations) {
                String name = ann.getQualifiedName();
                if (name != null && (name.endsWith(".Entity") || name.endsWith(".Document"))) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Extract field names and types from a class (for test data builder).
     */
    public Map<String, String> extractFields(PsiClass psiClass) {
        return ReadAction.compute(() -> {
            Map<String, String> fields = new LinkedHashMap<>();
            for (PsiField field : psiClass.getFields()) {
                // Skip static and final fields
                if (field.hasModifierProperty(PsiModifier.STATIC)
                        || field.hasModifierProperty(PsiModifier.FINAL)) {
                    continue;
                }
                String fieldName = field.getName();
                String typeName = field.getType().getPresentableText();
                fields.put(fieldName, typeName);
            }
            return fields;
        });
    }

    /**
     * Extract REST endpoints from a controller class.
     */
    public List<EndpointInfo> extractEndpoints(PsiClass psiClass) {
        return ReadAction.compute(() -> {
            List<EndpointInfo> endpoints = new ArrayList<>();
            for (PsiMethod method : psiClass.getMethods()) {
                for (PsiAnnotation ann : method.getAnnotations()) {
                    String qName = ann.getQualifiedName();
                    if (qName == null) continue;

                    String httpMethod = null;
                    if (qName.endsWith("GetMapping")) httpMethod = "GET";
                    else if (qName.endsWith("PostMapping")) httpMethod = "POST";
                    else if (qName.endsWith("PutMapping")) httpMethod = "PUT";
                    else if (qName.endsWith("DeleteMapping")) httpMethod = "DELETE";
                    else if (qName.endsWith("PatchMapping")) httpMethod = "PATCH";
                    else if (qName.endsWith("RequestMapping")) httpMethod = "REQUEST";

                    if (httpMethod != null) {
                        String path = extractAnnotationValue(ann);
                        String returnType = method.getReturnType() != null
                                ? method.getReturnType().getPresentableText() : "void";

                        List<String> paramTypes = new ArrayList<>();
                        for (PsiParameter param : method.getParameterList().getParameters()) {
                            paramTypes.add(param.getType().getPresentableText() + " " + param.getName());
                        }

                        endpoints.add(new EndpointInfo(httpMethod, path, method.getName(), returnType, paramTypes));
                    }
                }
            }
            return endpoints;
        });
    }

    /**
     * Build a comprehensive context string about a class for AI consumption.
     */
    public String buildAiContext(PsiJavaFile javaFile) {
        return ReadAction.compute(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Source Class Analysis ===\n\n");

            for (PsiClass psiClass : javaFile.getClasses()) {
                sb.append("Class: ").append(psiClass.getQualifiedName()).append("\n");

                // Annotations
                for (PsiAnnotation ann : psiClass.getAnnotations()) {
                    sb.append("  Annotation: @").append(getSimpleName(ann.getQualifiedName())).append("\n");
                }

                // Type
                if (isRestController(psiClass)) sb.append("  Type: REST Controller\n");
                else if (isEntity(psiClass)) sb.append("  Type: JPA Entity\n");
                else sb.append("  Type: Service/Component\n");

                sb.append("\n");

                // Fields
                for (PsiField field : psiClass.getFields()) {
                    sb.append("  Field: ").append(field.getType().getPresentableText())
                            .append(" ").append(field.getName()).append("\n");
                }

                sb.append("\n");

                // Methods
                for (PsiMethod method : psiClass.getMethods()) {
                    sb.append("  Method: ");
                    if (method.getReturnType() != null) {
                        sb.append(method.getReturnType().getPresentableText()).append(" ");
                    }
                    sb.append(method.getName()).append("(");
                    PsiParameter[] params = method.getParameterList().getParameters();
                    for (int i = 0; i < params.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(params[i].getType().getPresentableText())
                                .append(" ").append(params[i].getName());
                    }
                    sb.append(")\n");

                    // Method annotations (like @GetMapping)
                    for (PsiAnnotation ann : method.getAnnotations()) {
                        sb.append("    @").append(getSimpleName(ann.getQualifiedName()));
                        String val = extractAnnotationValue(ann);
                        if (!val.isEmpty()) sb.append("(\"").append(val).append("\")");
                        sb.append("\n");
                    }
                }
            }
            return sb.toString();
        });
    }

    // --- Private helpers ---

    private String extractClassDetails(PsiClass psiClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(psiClass.getName()).append(" {\n");
        for (PsiMethod method : psiClass.getMethods()) {
            sb.append("  ").append(method.getText()).append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String extractAnnotationValue(PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value == null) value = annotation.findAttributeValue(null);
        if (value != null) {
            String text = value.getText();
            // Remove quotes
            return text.replace("\"", "").replace("{", "").replace("}", "").trim();
        }
        return "";
    }

    private String getSimpleName(String qualifiedName) {
        if (qualifiedName == null) return "Unknown";
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    // --- Inner data classes ---

    public static class EndpointInfo {
        private final String httpMethod;
        private final String path;
        private final String methodName;
        private final String returnType;
        private final List<String> parameters;

        public EndpointInfo(String httpMethod, String path, String methodName,
                            String returnType, List<String> parameters) {
            this.httpMethod = httpMethod;
            this.path = path;
            this.methodName = methodName;
            this.returnType = returnType;
            this.parameters = parameters;
        }

        public String getHttpMethod() { return httpMethod; }
        public String getPath() { return path; }
        public String getMethodName() { return methodName; }
        public String getReturnType() { return returnType; }
        public List<String> getParameters() { return parameters; }
    }
}
