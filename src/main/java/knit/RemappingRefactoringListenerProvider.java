package knit;

import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import org.jetbrains.annotations.Nullable;

public class RemappingRefactoringListenerProvider implements RefactoringElementListenerProvider {
    @Nullable
    @Override
    public RefactoringElementListener getListener(PsiElement element) {
        return MappingService.getInstance(element.getProject()).hasMappings() ? new RemappingRefactoringListener(element) : null;
    }
}
