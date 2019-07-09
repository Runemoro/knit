package knit.mapping;

import java.util.ArrayList;
import java.util.List;

public class LocalVariableMapping {
    public int index;
    public String name;
    public final List<CommentLine> comments = new ArrayList<>();

    public LocalVariableMapping(int index, String name) {
        this.index = index;
        this.name = name;
    }
}
