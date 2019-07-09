package knit;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ObfuscationStatusPassFactory implements TextEditorHighlightingPassFactory {
    public ObfuscationStatusPassFactory(TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
        highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, null, true, -1);
    }

    @Nullable
    @Override
    public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
        return new ObfuscationStatusPass(file.getProject(), file, editor);
    }
}
