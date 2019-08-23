package knit;

import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import knit.mapping.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class MappingService {
    private static final String NO_PACKAGE_PACKAGE = "nopackage";

    private File mappingDirectory = null;
    private final Map<PsiClass, ClassMapping> classMappings = new HashMap<>();
    private final Map<PsiField, FieldMapping> fieldMappings = new HashMap<>();
    private final Map<PsiMethod, MethodMapping> methodMappings = new HashMap<>();
    private final Map<PsiParameter, LocalVariableMapping> parameterMappings = new HashMap<>();
    private final Map<String, ClassMapping> mappings = new HashMap<>();
    private final Map<ClassMapping, File> mappingFiles = new HashMap<>();

    public void loadMappings(File mappingDirectory) {
        clearMappings();
        this.mappingDirectory = mappingDirectory;
    }

    public void clearMappings() {
        mappingDirectory = null;
        clearCache();
    }

    public void clearCache() {
        classMappings.clear();
        fieldMappings.clear();
        methodMappings.clear();
        parameterMappings.clear();
        mappings.clear();
        mappingFiles.clear();
    }

    public boolean hasMappings() {
        return mappingDirectory != null;
    }

    public void markChanged(PsiElement element) {
        PsiClass rootClass = getRootClass(element);
        ClassMapping mapping = getMapping(rootClass);

        File file = mappingFiles.get(mapping);
        if (file != null) {
            file.delete();
        }

        File mappingFile = getMappingFile(mapping.name.replace('/', '.'));
        mappingFiles.put(mapping, mappingFile);

        try {
            MappingSerializer.writeClass(mappingFile, mapping);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getMappingName(String name) {
        if (name.startsWith(NO_PACKAGE_PACKAGE + ".")) {
            name = name.substring(name.indexOf('.') + 1);
        }

        return name.replace('.', '/');
    }

    public void attachMappings(PsiElement element) {
        new JavaRecursiveElementVisitor() {
            final Deque<ClassData> classes = new ArrayDeque<>();

            @Override
            public void visitClass(PsiClass clazz) {
                ClassMapping classMapping;
                if (classes.isEmpty()) {
                    classMapping = getMapping(getMappingName(clazz.getQualifiedName()));
                } else {
                    ClassData containingClassData = classes.peek();
                    String name = getClassName(clazz);
                    classMapping = containingClassData.nestedClasses.get(name);

                    if (classMapping == null) {
                        classMapping = new ClassMapping(name, name);
                        containingClassData.mapping.nestedClasses.add(classMapping);
                    }
                }

                classMappings.putIfAbsent(clazz, classMapping);
                ClassData classData = new ClassData(clazz, classMapping);

                classMapping.nestedClasses.forEach(nestedClass -> classData.nestedClasses.putIfAbsent(nestedClass.name, nestedClass));
                classMapping.methods.forEach(method -> classData.methods.putIfAbsent(method.name + " " + method.obfuscatedDescriptor, method));
                classMapping.fields.forEach(field -> classData.fields.putIfAbsent(field.name + " " + field.obfuscatedDescriptor, field));

                classes.push(classData);
                super.visitClass(clazz);
                classes.pop();
            }

            @Override
            public void visitMethod(PsiMethod method) {
                ClassData containingClassData = classes.peek();
                String obfuscatedDescriptor = getObfuscatedDescriptor(method);
                String methodName = method.isConstructor() ? "<init>" : method.getName();
                MethodMapping methodMapping = containingClassData.methods.get(methodName + " " + obfuscatedDescriptor);

                if (methodMapping == null) {
                    methodMapping = new MethodMapping(methodName, obfuscatedDescriptor, methodName);
                    containingClassData.mapping.methods.add(methodMapping);
                }

                methodMappings.putIfAbsent(method, methodMapping);

                Map<Integer, LocalVariableMapping> localVariables = new HashMap<>();
                methodMapping.localVariables.forEach(localVariable -> localVariables.putIfAbsent(localVariable.index, localVariable));

                int index = method.hasModifier(JvmModifier.STATIC) ? 0 : 1;
                for (PsiParameter parameter : method.getParameterList().getParameters()) {
                    LocalVariableMapping parameterMapping = localVariables.get(index);

                    if (parameterMapping == null) {
                        parameterMapping = new LocalVariableMapping(index, "arg" + index);
                        methodMapping.localVariables.add(parameterMapping);
                    }

                    parameterMappings.putIfAbsent(parameter, parameterMapping);
                    index += parameter.getType().equals(PsiType.LONG) || parameter.getType().equals(PsiType.DOUBLE) ? 2 : 1;
                }

                super.visitMethod(method);
            }

            @Override
            public void visitField(PsiField field) {
                ClassData containingClassData = classes.peek();
                String obfuscatedDescriptor = getObfuscatedDescriptor(field.getType());
                FieldMapping fieldMapping = containingClassData.fields.get(field.getName() + " " + obfuscatedDescriptor);

                if (fieldMapping == null) {
                    fieldMapping = new FieldMapping(field.getName(), obfuscatedDescriptor, field.getName());
                    containingClassData.mapping.fields.add(fieldMapping);
                }

                fieldMappings.put(field, fieldMapping);

                super.visitField(field);
            }

            class ClassData {
                public final PsiClass clazz;
                public final ClassMapping mapping;
                public final Map<String, ClassMapping> nestedClasses = new HashMap<>();
                public final Map<String, MethodMapping> methods = new HashMap<>();
                public final Map<String, FieldMapping> fields = new HashMap<>();

                ClassData(PsiClass clazz, ClassMapping mapping) {
                    this.clazz = clazz;
                    this.mapping = mapping;
                }
            }
        }.visitClass(getRootClass(element));
    }

    public ClassMapping getMapping(PsiClass clazz) {
        ClassMapping mapping = classMappings.get(clazz);

        if (mapping != null) {
            return mapping;
        }

        attachMappings(clazz);
        return classMappings.get(clazz);
    }

    public MethodMapping getMapping(PsiMethod method) {
        MethodMapping mapping = methodMappings.get(method);

        if (mapping != null) {
            return mapping;
        }

        attachMappings(method);
        return methodMappings.get(method);
    }

    public FieldMapping getMapping(PsiField field) {
        FieldMapping mapping = fieldMappings.get(field);

        if (mapping != null) {
            return mapping;
        }

        attachMappings(field);
        return fieldMappings.get(field);
    }

    public LocalVariableMapping getMapping(PsiParameter parameter) {
        LocalVariableMapping mapping = parameterMappings.get(parameter);

        if (mapping != null) {
            return mapping;
        }

        attachMappings(parameter);
        return parameterMappings.get(parameter);
    }

    private String getClassName(PsiClass clazz) {
        return clazz instanceof PsiAnonymousClass ?
                JavaAnonymousClassesHelper.getName((PsiAnonymousClass) clazz).substring(1) :
                clazz.getName();
    }

    private ClassMapping getMapping(String mappingName) {
        return mappings.computeIfAbsent(mappingName, k -> {
            try {
                File file = getMappingFile(mappingName);
                if (file.exists()) {
                    ClassMapping mapping = MappingSerializer.readClass(file);
                    mappingFiles.put(mapping, file);
                    return mapping;
                } else {
                    return new ClassMapping(mappingName, mappingName);
                }
            } catch (MappingSerializer.MappingFormatException e) {
                Messages.showErrorDialog(e.getMessage() + " in " + e.file + (e.line == -1 ? "" : " on line " + e.line), "Failed to Read Mapping File");
                return new ClassMapping(mappingName, mappingName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private PsiClass getRootClass(PsiElement element) {
        PsiClass topmostParent = PsiTreeUtil.getTopmostParentOfType(element, PsiClass.class);
        return topmostParent == null && element instanceof PsiClass ? (PsiClass) element : topmostParent;
    }

    private String getObfuscatedDescriptor(PsiMethod method) {
        StringBuilder descriptor = new StringBuilder();
        descriptor.append('(');

        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            descriptor.append(getObfuscatedDescriptor(parameter.getType()));
        }

        descriptor.append(')');
        descriptor.append(method.isConstructor() ? "V" : getObfuscatedDescriptor(method.getReturnType()));

        return descriptor.toString();
    }

    private String getObfuscatedDescriptor(PsiType type) {
        if (type instanceof PsiPrimitiveType) {
            return ((PsiPrimitiveType) type).getKind().getBinaryName();
        }

        if (type instanceof PsiClassType) {
            PsiClass resolved = ((PsiClassType) type).resolve();

            if (resolved instanceof PsiTypeParameter) {
                PsiClassType[] bounds = ((PsiTypeParameter) resolved).getExtendsList().getReferencedTypes();

                return bounds.length == 0 ? "Ljava/lang/Object;" : getObfuscatedDescriptor(bounds[0]);
            }

            if (resolved == null) {
                return "Lunresolved_class;";
            }

            Deque<String> nestedClassPath = new ArrayDeque<>();

            while (resolved.getContainingClass() != null) {
                nestedClassPath.addFirst(getClassName(resolved));
                resolved = resolved.getContainingClass();
            }

            ClassMapping mapping = getMapping(getMappingName(resolved.getQualifiedName()));

            StringBuilder obfuscatedName = new StringBuilder();
            obfuscatedName.append(mapping.obfuscatedName);
            for (String nestedClassName : nestedClassPath) {
                findNestedClassMapping:
                if (mapping != null) {
                    for (ClassMapping nestedClassMapping : mapping.nestedClasses) {
                        if (nestedClassMapping.name.equals(nestedClassName)) {
                            mapping = nestedClassMapping;
                            break findNestedClassMapping;
                        }

                        mapping = null;
                    }
                }

                obfuscatedName.append('$').append(mapping == null ? nestedClassName : mapping.obfuscatedName);
            }

            return "L" + obfuscatedName + ";";
        }

        if (type instanceof PsiArrayType) {
            return "[" + getObfuscatedDescriptor(((PsiArrayType) type).getComponentType());
        }

        throw new IllegalStateException("couldn't find descriptor for " + type);
    }

    private File getMappingFile(String name) {
        return new File(mappingDirectory, getMappingName(name) + ".mapping");
    }

    // TODO: settings
    public boolean isClassObfuscated(String name) {
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        return name.startsWith("class");
    }

    public boolean isMethodObfuscated(String name) {
        return name.startsWith("method") || name.startsWith("func");
    }

    public boolean isFieldObfuscated(String name) {
        return name.startsWith("field");
    }

    public static MappingService getInstance(Project project) {
        return ServiceManager.getService(project, MappingService.class);
    }
}
