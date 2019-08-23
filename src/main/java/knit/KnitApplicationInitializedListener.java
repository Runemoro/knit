package knit;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import org.jetbrains.annotations.NotNull;

public class KnitApplicationInitializedListener implements ApplicationInitializedListener {
    @Override
    public void componentsInitialized() {
        ApplicationManager
                .getApplication()
                .getMessageBus()
                .connect()
                .subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
                    @Override
                    public void projectOpened(@NotNull Project project) {
                        project.getMessageBus()
                                .connect()
                                .subscribe(
                                        RefactoringEventListener.REFACTORING_EVENT_TOPIC,
                                        new ParameterRefactoringEventListener(new RemappingRefactoringListenerProvider())
                                );
                    }
                });
    }
}
