package org.dimdev.knit;

import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.*;
import cuchaz.enigma.mapping.*;

public class RenameHandler {
    private final MappingsService mappingsService = ServiceManager.getService(MappingsService.class);

    public void handleRename(String oldName, PsiElement element) {
        if (!mappingsService.hasMappings()) {
            return;
        }

        if (element instanceof PsiClass) {
            ClassMapping mapping = getClassMapping(oldName);
            if (mapping != null) {
                mappingsService.getMappings().setClassDeobfName(mapping, getClassName((PsiClass) element));
            }
        }

        if (element instanceof PsiField) {
            ClassMapping classMapping = getClassMapping(getClassName(((PsiField) element).getContainingClass()));
            FieldMapping fieldMapping = getOrCreateFieldMapping((PsiField) element, oldName);
            classMapping.setFieldName(fieldMapping.getObfName(), fieldMapping.getObfDesc(), ((PsiField) element).getName());
        }

        if (element instanceof PsiMethod) {
            ClassMapping classMapping = getClassMapping(getClassName(((PsiMethod) element).getContainingClass()));
            MethodMapping methodMapping = getOrCreateMethodMapping((PsiMethod) element, oldName);
            classMapping.setMethodName(methodMapping.getObfName(), methodMapping.getObfDesc(), ((PsiMethod) element).getName());
        }

        if (element instanceof PsiParameter) {
            PsiElement declarationScope = ((PsiParameter) element).getDeclarationScope();
            if (declarationScope instanceof PsiMethod) {
                MethodMapping mapping = getOrCreateMethodMapping((PsiMethod) declarationScope, ((PsiMethod) declarationScope).getName());

                if (mapping != null) {
                    int index = ((PsiMethod) declarationScope).hasModifier(JvmModifier.STATIC) ? 0 : 1;
                    boolean found = false;
                    for (PsiParameter parameter : ((PsiMethod) declarationScope).getParameterList().getParameters()) {
                        if (parameter.equals(element)) {
                            found = true;
                            break;
                        }
                        index += parameter.getType().equals(PsiType.LONG) || parameter.getType().equals(PsiType.DOUBLE) ? 2 : 1;
                    }

                    if (found) {
                        mapping.setLocalVariableName(index, ((PsiParameter) element).getName());
                    }
                }
            }
        }
    }

    private ClassMapping getClassMapping(String className) {
        // Try getting deobfuscated class mapping
        ClassMapping mapping = mappingsService.getMappings().getClassByDeobf(className);

        // If that doesn't work, try getting obfuscated class mapping
        if (mapping == null) {
            mapping = mappingsService.getMappings().getClassByObf(className);
        }

        return mapping;
    }

    private MethodMapping getOrCreateMethodMapping(PsiMethod method, String actualName) {
        // Get mapping for containing class
        ClassMapping classMapping = getClassMapping(getClassName(method.getContainingClass()));
        if (classMapping == null) {
            return null;
        }

        // Try getting deobfuscated method mapping
        MethodDescriptor descriptor = obfuscateDescriptor(new MethodDescriptor(getDescriptor(method)));
        MethodMapping mapping = classMapping.getMethodByDeobf(actualName, descriptor);

        // If that doesn't work, try getting obfuscated method mapping
        if (mapping == null) {
            mapping = classMapping.getMethodByObf(actualName, descriptor);
        }

        // If that doesn't work either, create a new method mapping
        if (mapping == null) {
            mapping = new MethodMapping(actualName, descriptor);
            classMapping.addMethodMapping(mapping);
        }

        return mapping;
    }

    private FieldMapping getOrCreateFieldMapping(PsiField field, String actualName) {
        // Get mapping for containing class
        ClassMapping classMapping = getClassMapping(getClassName(field.getContainingClass()));
        if (classMapping == null) {
            return null;
        }

        // Try getting deobfuscated method mapping
        TypeDescriptor descriptor = obfuscateDescriptor(new TypeDescriptor(getDescriptor(field.getType())));
        FieldMapping mapping = classMapping.getFieldByDeobf(actualName, descriptor);

        // If that doesn't work, try getting obfuscated method mapping
        if (mapping == null) {
            mapping = classMapping.getFieldByObf(actualName, descriptor);
        }

        // If that doesn't work either, create a new field mapping
        if (mapping == null) {
            mapping = new FieldMapping(actualName, descriptor, actualName, Mappings.EntryModifier.UNCHANGED);
            classMapping.addFieldMapping(mapping);
        }

        return mapping;
    }

    public MethodDescriptor obfuscateDescriptor(MethodDescriptor descriptor) {
        return descriptor.remap(className -> {
            ClassMapping mapping = mappingsService.getMappings().getClassByDeobf(className);
            return mapping != null ? mapping.getObfFullName() : className;
        });
    }

    public TypeDescriptor obfuscateDescriptor(TypeDescriptor descriptor) {
        return descriptor.remap(clazzName -> {
            ClassMapping mapping = mappingsService.getMappings().getClassByDeobf(clazzName);
            return mapping != null ? mapping.getObfFullName() : clazzName;
        });
    }

    public static String getClassName(PsiClass element) {
        return JVMNameUtil.getClassVMName(element).replace('.', '/');
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
}
