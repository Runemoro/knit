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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RemappingRefactoringListener implements RefactoringElementListener, UndoRefactoringElementListener {
    private final MappingService mappingService;
    private final PsiElement element;
    private final String oldName;
    private final Consumer<String> setter;

    public RemappingRefactoringListener(PsiElement element) {
        mappingService = MappingService.getInstance(element.getProject());
        this.element = element;
        oldName = getQualifiedName(element);

        if (element instanceof PsiClass) {
            mappingService.attachMappings(element);
            ClassMapping mapping = mappingService.getMapping((PsiClass) element);
            setter = name -> {
                mapping.name = mappingService.getMappingName(name);
                mappingService.markChanged(element);
            };
        } else if (element instanceof PsiField) {
            mappingService.attachMappings(element);
            FieldMapping mapping = mappingService.getMapping((PsiField) element);
            setter = name -> {
                mapping.name = name;
                mappingService.markChanged(element);
            };
        } else if (element instanceof PsiMethod && !((PsiMethod) element).isConstructor()) {
            mappingService.attachMappings(element);
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
        } else if (element instanceof PsiPackage) {
            setter = createPackageNameSetter((PsiPackage) element);
        } else {
            setter = name -> {};
        }
    }

    private Consumer<String> createPackageNameSetter(PsiPackage element) {
        List<Consumer<String>> setters = new ArrayList<>();
        createPackageNameSetter("", element, setters);
        return name -> {
            for (Consumer<String> setter : setters) {
                setter.accept(name);
            }
        };
    }

    private void createPackageNameSetter(String nameRest, PsiPackage package_, List<Consumer<String>> setters) {
        for (PsiClass clazz : package_.getClasses()) {
            ClassMapping mapping = mappingService.getMapping(clazz);
            setters.add(name -> {
                mapping.name = (name + nameRest + "." + clazz.getName()).replace('.', '/');
                mappingService.markChanged(clazz);
            });
        }

        for (PsiPackage subpackage : package_.getSubPackages()) {
            createPackageNameSetter(nameRest + "." + subpackage.getName(), subpackage, setters);
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

            setter.accept(getQualifiedName(newElement));
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
        if (element instanceof PsiPackage) {
            return ((PsiPackage) element).getQualifiedName();
        }

        if (element instanceof PsiClass && ((PsiClass) element).getContainingClass() == null) {
            return ((PsiClass) element).getQualifiedName();
        }

        if (element instanceof PsiNamedElement) {
            return ((PsiNamedElement) element).getName();
        }

        return null;
    }
}
