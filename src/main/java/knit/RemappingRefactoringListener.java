package knit;

import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.UndoRefactoringElementListener;
import knit.mapping.ClassMapping;
import knit.mapping.FieldMapping;
import knit.mapping.LocalVariableMapping;
import knit.mapping.MethodMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class RemappingRefactoringListener implements RefactoringElementListener, UndoRefactoringElementListener {
    private final MappingService mappingService;
    private final PsiElement element;
    private final String oldName;
    private final Consumer<String> setter;

    public RemappingRefactoringListener(PsiElement element) {
        mappingService = MappingService.getInstance(element.getProject());;
        this.element = element;
        oldName = getQualifiedName(element);

        if (element instanceof PsiClass) {
            ClassMapping mapping = mappingService.getMapping((PsiClass) element);
            setter = name -> {
                mapping.name = mappingService.getMappingName(name);
                mappingService.markChanged(element);
            };
        } else if (element instanceof PsiField) {
            FieldMapping mapping = mappingService.getMapping((PsiField) element);
            setter = name -> {
                mapping.name = name;
                mappingService.markChanged(element);
            };
        } else if (element instanceof PsiMethod && !((PsiMethod) element).isConstructor()) {
            MethodMapping mapping = mappingService.getMapping((PsiMethod) element);
            setter = name -> {
                mapping.name = name;
                mappingService.markChanged(element);
            };
        } else if (element instanceof PsiParameter && ((PsiParameter) element).getDeclarationScope() instanceof PsiMethod) {
            LocalVariableMapping mapping = mappingService.getMapping((PsiParameter) element);
            setter = name -> {
                mapping.name = name;
                mappingService.markChanged(element);
            };
        } else {
            setter = name -> {};
        }
    }

    @Override
    public void elementMoved(@NotNull PsiElement newElement) {
        if (!(element instanceof PsiClass)) {
            return;
        }

        setter.accept(getQualifiedName(element));
    }

    @Override
    public void elementRenamed(@NotNull PsiElement newElement) {
        if (mappingService.hasMappings()) {
            if (element instanceof PsiMethod && ((PsiMethod) element).findSuperMethods().length != 0) {
                return;
            }

            setter.accept(getQualifiedName(element));
        }
    }

    @Override
    public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
        if (mappingService.hasMappings()) {
            setter.accept(oldName);
        }
    }

    @Nullable
    private String getQualifiedName(PsiElement element) {
        if (element instanceof PsiQualifiedNamedElement) {
            return ((PsiQualifiedNamedElement) element).getQualifiedName();
        } else if (element instanceof PsiNamedElement) {
            return ((PsiNamedElement) element).getName();
        } else {
            return null;
        }
    }
}
