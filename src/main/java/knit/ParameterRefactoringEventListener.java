package knit;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParameterRefactoringEventListener implements RefactoringEventListener {
    private final RemappingRefactoringListenerProvider listenerProvider;
    private RefactoringElementListener currentListener = null;

    ParameterRefactoringEventListener(RemappingRefactoringListenerProvider listenerProvider) {
        this.listenerProvider = listenerProvider;
    }

    @Override
    public void refactoringStarted(@NotNull String refactoringId, @Nullable RefactoringEventData beforeData) {
        if (beforeData == null) {
            return;
        }

        PsiElement element = beforeData.getUserData(RefactoringEventData.PSI_ELEMENT_KEY);
        if (element instanceof PsiParameter) {
            currentListener = listenerProvider.getListener(element);
        }
    }

    @Override
    public void refactoringDone(@NotNull String refactoringId, @Nullable RefactoringEventData afterData) {
        if (afterData == null) {
            return;
        }

        PsiElement element = afterData.getUserData(RefactoringEventData.PSI_ELEMENT_KEY);
        if (element instanceof PsiParameter) {
            currentListener.elementRenamed(element);
        }

        currentListener = null;
    }

    @Override
    public void conflictsDetected(@NotNull String refactoringId, @NotNull RefactoringEventData conflictsData) {}

    @Override
    public void undoRefactoring(@NotNull String refactoringId) {}
}
