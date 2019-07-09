package knit;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class EnableRemappingAction extends ToggleAction {
    private static final FileChooserDescriptor CHOOSE_FOLDER_DESCRIPTOR = new FileChooserDescriptor(false, true, false, false, false, false);

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return MappingService.getInstance(e.getProject()).hasMappings();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {

        if (state) {
            VirtualFile virtualFile = FileChooser.chooseFile(CHOOSE_FOLDER_DESCRIPTOR, null, null);

            if (virtualFile != null) {
                MappingService.getInstance(e.getProject()).loadMappings(new File(virtualFile.getPath()));
            }
        } else {
            MappingService.getInstance(e.getProject()).clearMappings();
        }
    }
}
