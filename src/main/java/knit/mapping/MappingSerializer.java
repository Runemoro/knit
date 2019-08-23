package knit.mapping;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MappingSerializer {
    private static final String CLASS_LABEL = "CLASS";
    private static final String FIELD_LABEL = "FIELD";
    private static final String METHOD_LABEL = "METHOD";
    private static final String LOCAL_VARIABLE_LABEL = "ARG";
    private static final String COMMENT_LABEL = "JAVADOC";

    public static void write(Iterable<ClassMapping> classes, File file) throws IOException {
        Files.walk(file.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);

        for (ClassMapping clazz : classes) {
            File mappingFile = new File(file, clazz.name + ".mapping");
            mappingFile.getParentFile().mkdirs();
            writeClass(mappingFile, clazz);
        }
    }

    public static void writeClass(File file, ClassMapping rootClass) throws IOException {
        rootClass = simplify(rootClass);

        if (rootClass == null) {
            if (file.exists()) {
                Files.delete(file.toPath());
                while (file.getParentFile().listFiles().length == 0) {
                    file = file.getParentFile();
                    Files.delete(file.toPath());
                }
            }

            return;
        }

        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            TreeSerializer.<Object>write(writer, rootClass, mapping -> {
                List<Object> children = new ArrayList<>();

                if (mapping instanceof ClassMapping) {
                    children.addAll(((ClassMapping) mapping).comments);
                    ((ClassMapping) mapping).nestedClasses.stream().sorted(Comparator.comparing(e -> e.obfuscatedName, MappingSerializer::compare)).forEach(children::add);
                    ((ClassMapping) mapping).fields.stream().sorted(Comparator.comparing(e -> e.obfuscatedName + e.obfuscatedDescriptor)).forEach(children::add);
                    ((ClassMapping) mapping).methods.stream().sorted(Comparator.comparing(e -> e.obfuscatedName + e.obfuscatedDescriptor)).forEach(children::add);
                }

                if (mapping instanceof MethodMapping) {
                    children.addAll(((MethodMapping) mapping).comments);
                    ((MethodMapping) mapping).localVariables.stream().sorted(Comparator.comparingInt(e -> e.index)).forEach(children::add);
                }

                if (mapping instanceof FieldMapping) {
                    children.addAll(((FieldMapping) mapping).comments);
                }

                if (mapping instanceof LocalVariableMapping) {
                    children.addAll(((LocalVariableMapping) mapping).comments);
                }

                return children;
            }, mapping -> {
                if (mapping instanceof ClassMapping) {
                    ClassMapping clazz = (ClassMapping) mapping;
                    return CLASS_LABEL + " " + formatName(clazz.obfuscatedName, clazz.name);
                }

                if (mapping instanceof FieldMapping) {
                    FieldMapping field = (FieldMapping) mapping;
                    return FIELD_LABEL + " " + formatName(field.obfuscatedName, field.name) + " " + field.obfuscatedDescriptor;
                }

                if (mapping instanceof MethodMapping) {
                    MethodMapping method = (MethodMapping) mapping;
                    return METHOD_LABEL + " " + formatName(method.obfuscatedName, method.name) + " " + method.obfuscatedDescriptor;
                }

                if (mapping instanceof LocalVariableMapping) {
                    LocalVariableMapping localVariable = (LocalVariableMapping) mapping;
                    return LOCAL_VARIABLE_LABEL + " " + localVariable.index + (("arg" + localVariable.index).equals(localVariable.name) ? "" : " " + localVariable.name);
                }

                if (mapping instanceof CommentLine) {
                    return COMMENT_LABEL + " " + ((CommentLine) mapping).comment;
                }

                if (mapping == null) {
                    throw new NullPointerException("null mapping");
                }

                throw new AssertionError("unknown mapping type" + mapping.getClass());
            });
        }
    }

    private static ClassMapping simplify(ClassMapping clazz) {
        ClassMapping simplified = new ClassMapping(clazz.obfuscatedName, clazz.name);
        boolean hasChildren = false;
        hasChildren |= simplified.nestedClasses.addAll(clazz.nestedClasses.stream().map(MappingSerializer::simplify).filter(Objects::nonNull).collect(Collectors.toSet()));
        hasChildren |= simplified.methods.addAll(clazz.methods.stream().map(MappingSerializer::simplify).filter(Objects::nonNull).collect(Collectors.toSet()));
        hasChildren |= simplified.fields.addAll(clazz.fields.stream().map(MappingSerializer::simplify).filter(Objects::nonNull).collect(Collectors.toSet()));
        hasChildren |= simplified.comments.addAll(clazz.comments);
        return hasChildren || !clazz.name.equals(clazz.obfuscatedName) ? simplified : null;
    }

    private static MethodMapping simplify(MethodMapping method) {
        MethodMapping simplified = new MethodMapping(method.obfuscatedName, method.obfuscatedDescriptor, method.name);
        boolean hasChildren = false;
        hasChildren |= simplified.localVariables.addAll(method.localVariables.stream().map(MappingSerializer::simplify).filter(Objects::nonNull).collect(Collectors.toSet()));
        hasChildren |= simplified.comments.addAll(method.comments);
        return hasChildren || !method.name.equals(method.obfuscatedName) ? simplified : null;
    }

    private static FieldMapping simplify(FieldMapping field) {
        return !field.name.equals(field.obfuscatedName) || !field.comments.isEmpty() ? field : null;
    }

    private static LocalVariableMapping simplify(LocalVariableMapping localVariable) {
        return !localVariable.name.equals("arg" + localVariable.index) || !localVariable.comments.isEmpty() ? localVariable : null;
    }

    private static int compare(String a, String b) {
        if (a.length() != b.length()) {
            return a.length() - b.length();
        }

        return a.compareTo(b);
    }

    private static String formatName(String obfuscatedName, String name) {
        return obfuscatedName.equals(name) ? obfuscatedName : obfuscatedName + " " + name;
    }

    public static Set<ClassMapping> read(File file) throws MappingFormatException, IOException {
        if (!file.isDirectory()) {
            throw new MappingFormatException("not a directory", file);
        }

        Set<ClassMapping> classes = new LinkedHashSet<>();

        for (File classFile : Files.walk(file.toPath()).map(Path::toFile).filter(File::isFile).collect(Collectors.toList())) {
            classes.add(readClass(classFile));
        }

        return classes;
    }

    public static ClassMapping readClass(File file) throws IOException, MappingFormatException {
        try (FileReader reader = new FileReader(file)) {
            List<Object> result = TreeSerializer.read(reader, (label, children, line) -> {
                String[] split = label.split(" ");
                switch (split[0]) {
                    case CLASS_LABEL: {
                        ClassMapping clazz;
                        if (split.length == 2) {
                            clazz = new ClassMapping(split[1], split[1]);
                        } else if (split.length == 3) {
                            clazz = new ClassMapping(split[1], split[2]);
                        } else {
                            throw new TreeSerializer.ParseException("invalid class entry", line);
                        }

                        for (Object child : children) {
                            if (child instanceof ClassMapping) {
                                clazz.nestedClasses.add((ClassMapping) child);
                            } else if (child instanceof MethodMapping) {
                                clazz.methods.add((MethodMapping) child);
                            } else if (child instanceof FieldMapping) {
                                clazz.fields.add((FieldMapping) child);
                            } else if (child instanceof CommentLine) {
                                clazz.comments.add((CommentLine) child);
                            } else {
                                throw new TreeSerializer.ParseException("class entry has invalid child", line);
                            }
                        }

                        return clazz;
                    }

                    case FIELD_LABEL: {
                        FieldMapping field;
                        if (split.length == 3) {
                            field = new FieldMapping(split[1], split[2], split[1]);
                        } else if (split.length == 4) {
                            field = new FieldMapping(split[1], split[3], split[2]);
                        } else {
                            throw new TreeSerializer.ParseException("invalid field entry", line);
                        }

                        for (Object child : children) {
                            if (child instanceof CommentLine) {
                                field.comments.add((CommentLine) child);
                            } else {
                                throw new TreeSerializer.ParseException("field entry has invalid child", line);
                            }
                        }

                        return field;
                    }

                    case METHOD_LABEL: {
                        MethodMapping method;
                        if (split.length == 3) {
                            method = new MethodMapping(split[1], split[2], split[1]);
                        } else if (split.length == 4) {
                            method = new MethodMapping(split[1], split[3], split[2]);
                        } else {
                            throw new TreeSerializer.ParseException("invalid method entry", line);
                        }

                        for (Object child : children) {
                            if (child instanceof LocalVariableMapping) {
                                method.localVariables.add((LocalVariableMapping) child);
                            } else if (child instanceof CommentLine) {
                                method.comments.add((CommentLine) child);
                            } else {
                                throw new TreeSerializer.ParseException("method entry has invalid child", line);
                            }
                        }

                        return method;
                    }

                    case LOCAL_VARIABLE_LABEL: {
                        LocalVariableMapping localVariable;
                        if (split.length == 3) {
                            int index;
                            try {
                                index = Integer.parseInt(split[1]);
                            } catch (NumberFormatException e) {
                                throw new TreeSerializer.ParseException("argument index not a number", line);
                            }

                            localVariable = new LocalVariableMapping(index, split[2]);
                        } else {
                            throw new TreeSerializer.ParseException("invalid local variable entry", line);
                        }

                        for (Object child : children) {
                            if (child instanceof CommentLine) {
                                localVariable.comments.add((CommentLine) child);
                            } else {
                                throw new TreeSerializer.ParseException("field entry has invalid child", line);
                            }
                        }

                        return localVariable;
                    }


                    default: {
                        throw new TreeSerializer.ParseException("unknown entry type", line);
                    }
                }
            });

            if (result.size() > 1) {
                throw new MappingFormatException("two top-level entries in the same file", file);
            }

            if (!(result.get(0) instanceof ClassMapping)) {
                throw new MappingFormatException("top-level entry isn't a class entry", file);
            }

            return (ClassMapping) result.get(0);
        } catch (TreeSerializer.ParseException e) {
            throw new MappingFormatException(e, file);
        }
    }

    public static class MappingFormatException extends Exception {
        public final File file;
        public final int line;

        public MappingFormatException(String message, File file) {
            super(message);
            this.file = file;
            line = -1;
        }

        public MappingFormatException(TreeSerializer.ParseException cause, File file) {
            super(cause.getMessage(), cause);
            this.file = file;
            line = cause.line;
        }

        @Override
        public String toString() {
            if (line == -1) {
                return getClass().getName() + ": " + getMessage() + " (" + file + ")";
            } else {
                return getClass().getName() + ": " + getMessage() + " (" + file + ":" + line + ")";
            }
        }
    }
}
