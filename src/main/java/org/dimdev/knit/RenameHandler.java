package org.dimdev.knit;

import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.*;
import cuchaz.enigma.mapping.*;
import cuchaz.enigma.throwables.MappingConflict;

import java.util.function.Supplier;

public class RenameHandler {
    public static final String NO_PACKAGE_PREFIX = "nopackage/";
    private final MappingsService mappingsService = ServiceManager.getService(MappingsService.class);

    public void handleRename(String oldName, PsiElement element) {
        if (!mappingsService.hasMappings()) {
            return;
        }

        if (element instanceof PsiClass) {
            PsiClass clazz = (PsiClass) element;

            ClassMapping mapping = getOrCreateClassMapping(oldName);
            if (mapping != null) {
                int dollarSignIndex = oldName.indexOf('$');
                if (dollarSignIndex == -1) {
                    mappingsService.getMappings().setClassDeobfName(mapping, getClassName(clazz));
                } else {
                    ClassMapping parent = getOrCreateClassMapping(oldName.substring(0, dollarSignIndex));
                    parent.setInnerClassName(mapping.getObfEntry(), clazz.getName());
                }
            }
        }

        if (element instanceof PsiField) {
            PsiField field = (PsiField) element;

            ClassMapping classMapping = getOrCreateClassMapping(getClassName(field.getContainingClass()));
            FieldMapping fieldMapping = getOrCreateFieldMapping(field, oldName);
            classMapping.setFieldName(fieldMapping.getObfName(), fieldMapping.getObfDesc(), field.getName());
        }

        if (element instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) element;

            if (method.isConstructor()) {
                return;
            }

            ClassMapping classMapping = getOrCreateClassMapping(getClassName(method.getContainingClass()));
            MethodMapping methodMapping = getOrCreateMethodMapping(method, oldName);
            classMapping.setMethodName(methodMapping.getObfName(), methodMapping.getObfDesc(), method.getName());
        }

        if (element instanceof PsiParameter) {
            PsiParameter parameter = (PsiParameter) element;

            PsiElement declarationScope = parameter.getDeclarationScope();
            if (declarationScope instanceof PsiMethod) {
                MethodMapping mapping = getOrCreateMethodMapping((PsiMethod) declarationScope, ((PsiMethod) declarationScope).getName());

                if (mapping != null) {
                    int index = ((PsiMethod) declarationScope).hasModifier(JvmModifier.STATIC) ? 0 : 1;
                    boolean found = false;
                    for (PsiParameter currentParameter : ((PsiMethod) declarationScope).getParameterList().getParameters()) {
                        if (currentParameter.equals(parameter)) {
                            found = true;
                            break;
                        }
                        index += currentParameter.getType().equals(PsiType.LONG) || currentParameter.getType().equals(PsiType.DOUBLE) ? 2 : 1;
                    }

                    if (found) {
                        mapping.setLocalVariableName(index, parameter.getName());
                    }
                }
            }
        }
    }

    private ClassMapping getOrCreateClassMapping(String className) {
        String[] parts = className.split("\\$");

        // Get or create root mapping
        ClassMapping mapping = coalesce(
                () -> mappingsService.getMappings().getClassByDeobf(parts[0]),
                () -> mappingsService.getMappings().getClassByObf(parts[0]),
                () -> {
                    try {
                        ClassMapping newMapping = new ClassMapping(className);
                        mappingsService.getMappings().addClassMapping(newMapping);
                        return newMapping;
                    } catch (MappingConflict e) {
                        throw new IllegalStateException(e);
                    }
                }
        );

        // Get or create subclass mappings
        for (int i = 1; i < parts.length && mapping != null; i++) {
            ClassMapping mapping_ = mapping;
            int i_ = i;
            mapping = coalesce(
                    () -> mapping_.getInnerClassByDeobf(parts[i_]),
                    () -> mapping_.getInnerClassByObfSimple(parts[i_]),
                    () -> {
                        try {
                            ClassMapping newMapping = new ClassMapping(className);
                            mapping_.addInnerClassMapping(newMapping);
                            return newMapping;
                        } catch (MappingConflict e) {
                            throw new IllegalStateException(e);
                        }
                    }
            );
        }

        return mapping;
    }

    private MethodMapping getOrCreateMethodMapping(PsiMethod method, String actualName) {
        if (method.isConstructor()) {
            actualName = "<init>";
        }

        // Get mapping for containing class
        ClassMapping classMapping = getOrCreateClassMapping(getClassName(method.getContainingClass()));
        if (classMapping == null) {
            return null;
        }

        MethodDescriptor descriptor = new MethodDescriptor(getDescriptor(method)).remap(this::obfuscateClassName);
        String actualName_ = actualName;
        return coalesce(
                () -> classMapping.getMethodByDeobf(actualName_, descriptor),
                () -> classMapping.getMethodByObf(actualName_, descriptor),
                () -> {
                    MethodMapping newMapping = new MethodMapping(actualName_, descriptor);
                    classMapping.addMethodMapping(newMapping);
                    return newMapping;
                });
    }

    private FieldMapping getOrCreateFieldMapping(PsiField field, String actualName) {
        // Get mapping for containing class
        ClassMapping classMapping = getOrCreateClassMapping(getClassName(field.getContainingClass()));
        if (classMapping == null) {
            return null;
        }

        TypeDescriptor descriptor = new TypeDescriptor(getDescriptor(field.getType())).remap(this::obfuscateClassName);
        return coalesce(
                () -> classMapping.getFieldByDeobf(actualName, descriptor),
                () -> classMapping.getFieldByObf(actualName, descriptor),
                () -> {
                    FieldMapping newMapping = new FieldMapping(actualName, descriptor, actualName, Mappings.EntryModifier.UNCHANGED);
                    classMapping.addFieldMapping(newMapping);
                    return newMapping;
                });
    }

    private String obfuscateClassName(String className) {
        StringBuilder obfuscatedName = new StringBuilder();

        String[] parts = className.split("\\$");

        // Append root class
        ClassMapping mapping = coalesce(
                () -> mappingsService.getMappings().getClassByDeobf(parts[0]),
                () -> mappingsService.getMappings().getClassByObf(parts[0])
        );
        obfuscatedName.append(mapping != null ? mapping.getObfFullName() : parts[0]);

        // Append inner classes
        for (int i = 1; i < parts.length; i++) {
            if (mapping != null) {
                ClassMapping mapping_ = mapping;
                int i_ = i;
                mapping = coalesce(
                        () -> mapping_.getInnerClassByDeobf(parts[i_]),
                        () -> mapping_.getInnerClassByObfSimple(parts[i_])
                );
            }
            obfuscatedName.append("$").append(mapping != null ? mapping.getObfSimpleName() : parts[i]);
        }

        return obfuscatedName.toString();
    }

    public static String getClassName(PsiClass element) {
        String className = JVMNameUtil.getClassVMName(element).replace('.', '/');

        if (className.startsWith(NO_PACKAGE_PREFIX)) {
            className = className.substring(NO_PACKAGE_PREFIX.length());
        }
        return className;
    }

    public static String getDescriptor(PsiType type) {
        StringBuilder descriptor = new StringBuilder();
        while (type instanceof PsiArrayType) {
            descriptor.append('[');
            type = ((PsiArrayType) type).getComponentType();
        }

        String primitiveSignature = JVMNameUtil.getPrimitiveSignature(type.getCanonicalText());
        if (primitiveSignature != null) {
            descriptor.append(primitiveSignature);
        } else {
            descriptor.append('L')
                      .append(JVMNameUtil.getJVMQualifiedName(type).getDisplayName(null).replace('.', '/'))
                      .append(';');
        }

        return descriptor.toString();
    }

    public static String getDescriptor(PsiMethod method) {
        return JVMNameUtil.getJVMSignature(method).getDisplayName(null);
    }

    @SafeVarargs
    private static <T> T coalesce(Supplier<T>... suppliers) {
        for (Supplier<T> supplier : suppliers) {
            T item = supplier.get();
            if (item != null) {
                return item;
            }
        }
        return null;
    }
}
