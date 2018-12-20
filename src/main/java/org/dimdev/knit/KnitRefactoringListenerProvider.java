package org.dimdev.knit;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KnitRefactoringListenerProvider implements RefactoringElementListenerProvider {
    private final MappingsService mappingsService = ServiceManager.getService(MappingsService.class);
    private final RenameHandler renameHandler = new RenameHandler();

    @Nullable
    @Override
    public RefactoringElementListener getListener(PsiElement element) {
        if (!mappingsService.hasMappings()) {
            return null;
        }

        String oldName;
        if (element instanceof PsiClass) {
            oldName = RenameHandler.getClassName((PsiClass) element);
        } else if (element instanceof PsiMethod) {
            oldName = ((PsiMethod) element).getName();
        } else if (element instanceof PsiField) {
            oldName = ((PsiField) element).getName();
        } else {
            oldName = null;
        }

        return new RefactoringElementListener() {
            @Override
            public void elementMoved(@NotNull PsiElement newElement) {
                if (element instanceof PsiClass) {
                    renameHandler.handleRename(oldName, newElement);
                }
            }

            @Override
            public void elementRenamed(@NotNull PsiElement newElement) {
                renameHandler.handleRename(oldName, newElement);
            }
        };
    }
}
