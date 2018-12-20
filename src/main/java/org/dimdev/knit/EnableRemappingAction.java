package org.dimdev.knit;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class EnableRemappingAction extends ToggleAction {
    private static final FileChooserDescriptor CHOOSE_FOLDER_DESCRIPTOR = new FileChooserDescriptor(false, true, false, false, false, false);
    public final MappingsService mappingsService = ServiceManager.getService(MappingsService.class);

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return mappingsService.hasMappings();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        if (state) {
            // TODO: use custom dialog with "Select Enigma mappings folder" title
            VirtualFile virtualFile = FileChooser.chooseFile(CHOOSE_FOLDER_DESCRIPTOR, null, null);

            if (virtualFile != null) {
                mappingsService.loadMappings(new File(virtualFile.getPath()));
            }
        } else {
            mappingsService.saveMappings();
            mappingsService.clearMappings();
        }
    }
}
