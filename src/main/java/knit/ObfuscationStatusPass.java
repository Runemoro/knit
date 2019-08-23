package knit;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ui.JBColor;
import knit.mapping.ClassMapping;
import knit.mapping.FieldMapping;
import knit.mapping.LocalVariableMapping;
import knit.mapping.MethodMapping;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ObfuscationStatusPass extends TextEditorHighlightingPass { // TODO: settings
    private static final TextAttributesKey OBFUSCATION_STATUS_KEY = TextAttributesKey.createTextAttributesKey("OBFUSCATION_HIGHLIGHTING");
    private static final HighlightInfoType TYPE = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, OBFUSCATION_STATUS_KEY);
    private static final JBColor BLUE = new JBColor(new Color(0, 128, 255), new Color(0, 128, 255));
    private static final JBColor GREEN = new JBColor(new Color(0, 255, 128), new Color(0, 255, 128));
    private static final JBColor ORANGE = new JBColor(new Color(255, 128, 0), new Color(255, 128, 0));
    private static final TextAttributes UNMAPPED_STYLE = new TextAttributes(null, BLUE.brighter(), null, null, Font.PLAIN);
    private static final TextAttributes UNOBFUSCATED_STYLE = new TextAttributes(null, GREEN, null, null, Font.PLAIN);
    private static final TextAttributes UNOBFUSCATED_REMAPPED_STYLE = new TextAttributes(null, ORANGE.brighter(), null, null, Font.PLAIN);

    static {
        UNMAPPED_STYLE.setErrorStripeColor(BLUE);
        UNOBFUSCATED_REMAPPED_STYLE.setErrorStripeColor(ORANGE);
    }

    private final PsiFile file;
    private final MappingService mappingService;

    public ObfuscationStatusPass(Project project, PsiFile file, Editor editor) {
        super(project, editor.getDocument(), true);
        this.file = file;
        mappingService = MappingService.getInstance(project);
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {}

    @Override
    public void doApplyInformationToEditor() {
        if (!canHighlight(file)) {
            return;
        }

        List<HighlightInfo> highlights = new ArrayList<>();

        new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitClass(PsiClass clazz) {
                super.visitClass(clazz);
                if (clazz instanceof PsiTypeParameter || clazz.getNameIdentifier() == null) {
                    return;
                }

                ClassMapping mapping = mappingService.getMapping(clazz);
                mark(clazz, mapping.obfuscatedName, !mapping.name.equals(mapping.obfuscatedName), mappingService.isClassObfuscated(mapping.obfuscatedName));
            }

            @Override
            public void visitField(PsiField field) {
                super.visitField(field);
                FieldMapping mapping = mappingService.getMapping(field);
                mark(field, mapping.obfuscatedName, !mapping.name.equals(mapping.obfuscatedName), mappingService.isFieldObfuscated(mapping.obfuscatedName));
            }

            @Override
            public void visitMethod(PsiMethod method) {
                super.visitMethod(method);
                PsiMethod[] superMethods = method.findDeepestSuperMethods();
                if (superMethods.length != 0) {
                    for (PsiMethod superMethod : superMethods) {
                        if (superMethod.getContainingFile().isWritable()) {
                            MethodMapping mapping = mappingService.getMapping(superMethod);
                            mark(method, mapping.obfuscatedName, !mapping.name.equals(mapping.obfuscatedName), mappingService.isMethodObfuscated(mapping.obfuscatedName));
                            break;
                        }
                    }

                    return;
                }

                if (method.isConstructor()) {
                    PsiClass clazz = method.getContainingClass();
                    ClassMapping mapping = mappingService.getMapping(clazz);
                    mark(clazz, mapping.obfuscatedName, !mapping.name.equals(mapping.obfuscatedName), mappingService.isClassObfuscated(mapping.obfuscatedName));
                    return;
                }

                MethodMapping mapping = mappingService.getMapping(method);
                mark(method, mapping.obfuscatedName, !mapping.name.equals(mapping.obfuscatedName), mappingService.isMethodObfuscated(mapping.obfuscatedName));
            }

            @Override
            public void visitParameter(PsiParameter parameter) {
                if (!(parameter.getDeclarationScope() instanceof PsiMethod)) {
                    return;
                }

                PsiMethod[] superMethods = ((PsiMethod) parameter.getDeclarationScope()).findDeepestSuperMethods();
                if (superMethods.length != 0) {
                    return;
                }

                LocalVariableMapping mapping = mappingService.getMapping(parameter);
                mark(parameter, null, !mapping.name.equals("arg" + mapping.index), true);
                super.visitParameter(parameter);
            }

            private void mark(PsiNameIdentifierOwner nameIdentifier, String obfuscatedName, boolean mapped, boolean obfuscated) {
                HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(TYPE);
                builder.range(nameIdentifier.getNameIdentifier().getTextRange());

                if (!mapped) {
                    if (obfuscated) {
                        builder.textAttributes(UNMAPPED_STYLE);
                        builder.descriptionAndTooltip("Obfuscated");
                    } else {
                        builder.textAttributes(UNOBFUSCATED_STYLE);
                        builder.descriptionAndTooltip("Not obfuscated");
                    }
                } else {
                    if (obfuscated) {
                        // builder.textAttributes(MAPPED_STYLE);
                        builder.descriptionAndTooltip("Mapped (old name: " + obfuscatedName + ")");
                    } else {
                        builder.textAttributes(UNOBFUSCATED_REMAPPED_STYLE);
                        builder.descriptionAndTooltip("Renamed unobfuscated (old name: " + obfuscatedName + ")");
                    }
                }

                highlights.add(builder.createUnconditionally());
            }
        }.visitFile(file);

        UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, file.getTextLength(), highlights, getColorsScheme(), getId());
    }

    private boolean canHighlight(PsiFile file) {
        if (!mappingService.hasMappings()) {
            return false;
        }

        for (PsiElement child : file.getChildren()) {
            if (child instanceof PsiClass && child.getContainingFile().isWritable()) {
                return true;
            }
        }
        return false;
    }
}
