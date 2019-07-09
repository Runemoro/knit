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
import com.intellij.openapi.util.TextRange;
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

public class ObfuscationStatusPass extends TextEditorHighlightingPass {
    private static final TextAttributesKey OBFUSCATION_HIGHLIGHTING_KEY = TextAttributesKey.createTextAttributesKey("OBFUSCATION_HIGHLIGHTING_KEY");
    private static final HighlightInfoType TYPE = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, OBFUSCATION_HIGHLIGHTING_KEY);
    private static final TextAttributes UNMAPPED_STYLE = new TextAttributes(null, new JBColor(new Color(255, 128, 128), new Color(255, 128, 128)), null, null, Font.PLAIN);
    private static final TextAttributes MAPPED_STYLE = new TextAttributes(null, new JBColor(new Color(200, 255, 128), new Color(200, 255, 128)), null, null, Font.PLAIN);

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

        List<HighlightInfo> infos = getHighlights();
        UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, file.getTextLength(), infos, getColorsScheme(), getId());
    }

    private List<HighlightInfo> getHighlights() {
        List<HighlightInfo> highlights = new ArrayList<>();

        new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitClass(PsiClass clazz) {
                super.visitClass(clazz);
                if (clazz instanceof PsiTypeParameter || clazz.getNameIdentifier() == null) {
                    return;
                }

                ClassMapping mapping = mappingService.getMapping(clazz);
                mark(clazz, !mapping.name.equals(mapping.obfuscatedName));
            }

            private void mark(PsiNameIdentifierOwner nameIdentifier, boolean mapped) {
                if (!mapped) {
                    highlights.add(createHighlightInfo(nameIdentifier.getNameIdentifier().getTextRange(), mapped ? MAPPED_STYLE : UNMAPPED_STYLE));
                }
            }

            @Override
            public void visitField(PsiField field) {
                super.visitField(field);
                FieldMapping mapping = mappingService.getMapping(field);
                mark(field, !mapping.name.equals(mapping.obfuscatedName));
            }

            @Override
            public void visitMethod(PsiMethod method) {
                super.visitMethod(method);
                PsiMethod[] superMethods = method.findDeepestSuperMethods();
                if (superMethods.length != 0) {
                    for (PsiMethod superMethod : superMethods) {
                        if (superMethod.getContainingFile().isWritable()) {
                            MethodMapping mapping = mappingService.getMapping(superMethod);
                            mark(method, !mapping.name.equals(mapping.obfuscatedName));
                            break;
                        }
                    }

                    return;
                }

                if (method.isConstructor()) {
                    PsiClass clazz = method.getContainingClass();
                    ClassMapping mapping = mappingService.getMapping(clazz);
                    mark(clazz, !mapping.name.equals(mapping.obfuscatedName));
                    return;
                }

                MethodMapping mapping = mappingService.getMapping(method);
                mark(method, !mapping.name.equals(mapping.obfuscatedName));
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
                mark(parameter, !mapping.name.equals("arg" + mapping.index));
                super.visitParameter(parameter);
            }
        }.visitFile(file);

        return highlights;
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

    private static HighlightInfo createHighlightInfo(TextRange range, TextAttributes attributes) {
        return HighlightInfo.newHighlightInfo(TYPE).range(range).textAttributes(attributes).severity(HighlightSeverity.INFORMATION).createUnconditionally();
    }
}
