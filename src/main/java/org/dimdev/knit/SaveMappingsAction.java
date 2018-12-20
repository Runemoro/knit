package org.dimdev.knit;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

public class SaveMappingsAction extends AnAction {
    private final MappingsService mappingsService = ServiceManager.getService(MappingsService.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        mappingsService.saveMappings();
    }
}
