package knit;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.*;
import com.intellij.ui.ColoredTreeCellRenderer;
import knit.mapping.MethodMapping;

public class ObfuscationProjectViewNodeDecorator implements ProjectViewNodeDecorator { // TODO: settings, improve performance
    @Override
    public void decorate(ProjectViewNode node, PresentationData data) {
        MappingService mappingService = MappingService.getInstance(node.getProject());
        PsiElement element = getElement(node);

        if (!(element instanceof PsiClass) || !mappingService.hasMappings()) {
            return;
        }

        int[] methods = {0};
        int[] mappedMethods = {0};

        new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethod(PsiMethod method) {
                if (method.isConstructor() || method.findSuperMethods().length > 0) {
                    super.visitMethod(method);
                    return;
                }

                MethodMapping mapping = mappingService.getMapping(method);

                if (!mapping.name.equals(mapping.obfuscatedName) && mappingService.isMethodObfuscated(mapping.obfuscatedName)) {
                    mappedMethods[0]++;
                }

                methods[0]++;

                super.visitMethod(method);
            }
        }.visitElement(element);

        data.setLocationString(methods[0] - mappedMethods[0] + " unmapped methods");
    }

    @Override
    public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {

    }

    private PsiElement getElement(ProjectViewNode node) {
        Object value = node.getValue();

        if (value instanceof PsiElement) {
            return (PsiElement) value;
        } else if (value instanceof SmartPsiElementPointer) {
            return ((SmartPsiElementPointer) value).getElement();
        }

        return null;
    }
}
