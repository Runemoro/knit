package org.dimdev.knit;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KnitApplicationInitializedListener implements ApplicationInitializedListener {
    @Override
    public void componentsInitialized() {
        MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
        messageBus.connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectOpened(@NotNull Project project) {
                project.getMessageBus().connect().subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, new RefactoringEventListener() {
                    private RenameHandler renameHandler = new RenameHandler();

                    @Override
                    public void refactoringStarted(@NotNull String refactoringId, @Nullable RefactoringEventData beforeData) {}

                    @Override
                    public void refactoringDone(@NotNull String refactoringId, @Nullable RefactoringEventData afterData) {
                        PsiElement element = afterData.getUserData(RefactoringEventData.PSI_ELEMENT_KEY);
                        if (element instanceof PsiParameter) {
                            renameHandler.handleRename(null, element);
                        }
                    }

                    @Override
                    public void conflictsDetected(@NotNull String refactoringId, @NotNull RefactoringEventData conflictsData) {}

                    @Override
                    public void undoRefactoring(@NotNull String refactoringId) {}
                });
            }
        });
    }
}
