package knit.mapping;

import java.util.ArrayList;
import java.util.List;

public class FieldMapping {
    public String obfuscatedName;
    public String obfuscatedDescriptor;
    public String name;
    public final List<CommentLine> comments = new ArrayList<>();

    public FieldMapping(String obfuscatedName, String obfuscatedDescriptor, String name) {
        this.obfuscatedName = obfuscatedName;
        this.obfuscatedDescriptor = obfuscatedDescriptor;
        this.name = name;
    }
}
