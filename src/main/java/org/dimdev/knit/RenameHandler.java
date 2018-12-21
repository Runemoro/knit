package org.dimdev.knit;

import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.*;
import cuchaz.enigma.mapping.*;
import cuchaz.enigma.throwables.MappingConflict;

public class RenameHandler {
    public static final String NO_PACKAGE_PREFIX = "nopackage/";
    public static final int MAX_OBFUSCATED_NAME_LENGTH = 3;
    private final MappingsService mappingsService = ServiceManager.getService(MappingsService.class);

    public void handleRename(String oldName, PsiElement element) {
        if (!mappingsService.hasMappings()) {
            return;
        }

        if (element instanceof PsiClass) {
            PsiClass clazz = (PsiClass) element;

            ClassMapping mapping = getOrCreateClassMapping(oldName);
            if (mapping != null) {
                mappingsService.getMappings().setClassDeobfName(mapping, getClassName(clazz));
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
        // Try getting deobfuscated class mapping
        ClassMapping mapping = mappingsService.getMappings().getClassByDeobf(className);

        // If that doesn't work, try getting obfuscated class mapping
        if (mapping == null) {
            mapping = mappingsService.getMappings().getClassByObf(className);
        }

        // If that doesn't work either, create a new class mapping
        if (mapping == null && className.length() <= MAX_OBFUSCATED_NAME_LENGTH) {
            try {
                mapping = new ClassMapping(className);
                mappingsService.getMappings().addClassMapping(mapping);
            } catch (MappingConflict e) {
                throw new IllegalStateException("Both getClassByDeobf and getClassByObf returned null yet addClassMapping " +
                                                "threw a MappingConflict exception", e);
            }
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

        // Try getting deobfuscated method mapping
        MethodDescriptor descriptor = obfuscateDescriptor(new MethodDescriptor(getDescriptor(method)));
        MethodMapping mapping = classMapping.getMethodByDeobf(actualName, descriptor);

        // If that doesn't work, try getting obfuscated method mapping
        if (mapping == null) {
            mapping = classMapping.getMethodByObf(actualName, descriptor);
        }

        // If that doesn't work either, create a new method mapping
        if (mapping == null && actualName.length() <= MAX_OBFUSCATED_NAME_LENGTH) {
            mapping = new MethodMapping(actualName, descriptor);
            classMapping.addMethodMapping(mapping);
        }

        return mapping;
    }

    private FieldMapping getOrCreateFieldMapping(PsiField field, String actualName) {
        // Get mapping for containing class
        ClassMapping classMapping = getOrCreateClassMapping(getClassName(field.getContainingClass()));
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
        if (mapping == null && actualName.length() <= MAX_OBFUSCATED_NAME_LENGTH) {
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
}
