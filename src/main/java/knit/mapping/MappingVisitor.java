package knit.mapping;

import java.util.Set;

public abstract class MappingVisitor {
    public void visit(Set<ClassMapping> mappings) {
        mappings.forEach(this::visit);
    }

    public void visit(ClassMapping clazz) {
        clazz.nestedClasses.forEach(this::visit);
        clazz.fields.forEach(this::visit);
        clazz.methods.forEach(this::visit);
        clazz.comments.forEach(this::visit);
    }

    public void visit(FieldMapping field) {
        field.comments.forEach(this::visit);
    }

    public void visit(MethodMapping method) {
        method.localVariables.forEach(this::visit);
        method.comments.forEach(this::visit);
    }

    public void visit(LocalVariableMapping localVariable) {
        localVariable.comments.forEach(this::visit);
    }

    public void visit(CommentLine commentLine) {}
}
