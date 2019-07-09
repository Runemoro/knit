package knit.mapping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MethodMapping {
    public String obfuscatedName;
    public String obfuscatedDescriptor;
    public String name;
    public final Set<LocalVariableMapping> localVariables = new LinkedHashSet<>();
    public final List<CommentLine> comments = new ArrayList<>();

    public MethodMapping(String obfuscatedName, String obfuscatedDescriptor, String name) {
        this.obfuscatedName = obfuscatedName;
        this.obfuscatedDescriptor = obfuscatedDescriptor;
        this.name = name;
    }
}
