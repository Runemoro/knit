package knit.mapping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ClassMapping {
    public String obfuscatedName;
    public String name;
    public final Set<ClassMapping> nestedClasses = new LinkedHashSet<>();
    public final Set<FieldMapping> fields = new LinkedHashSet<>();
    public final Set<MethodMapping> methods = new LinkedHashSet<>();
    public final List<CommentLine> comments = new ArrayList<>();

    public ClassMapping(String obfuscatedName, String name) {
        this.obfuscatedName = obfuscatedName;
        this.name = name;
    }
}
