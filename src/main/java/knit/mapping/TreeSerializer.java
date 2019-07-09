package knit.mapping;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.*;
import java.util.function.Function;

public class TreeSerializer {
    private TreeSerializer() {}

    public static <E> void write(Writer writer, E root, Function<E, List<E>> childrenProvider, Function<E, String> labelSerializer) throws IOException {
        write(writer, root, childrenProvider, labelSerializer, 0);
    }

    private static <E> void write(Writer writer, E root, Function<E, List<E>> childrenProvider, Function<E, String> labelProvider, int indent) throws IOException {
        for (int i = 0; i < indent; i++) {
            writer.write('\t');
        }

        writer.write(labelProvider.apply(root));
        writer.write('\n');

        for (E child : childrenProvider.apply(root)) {
            write(writer, child, childrenProvider, labelProvider, indent + 1);
        }
    }

    public static <E> List<E> read(Reader reader, Factory<E> factory) throws ParseException, IOException {
        int lastIndent = 0;
        int indent = 0;
        int line = 1;
        Deque<String> labels = new ArrayDeque<>();
        Deque<Integer> lines = new ArrayDeque<>();
        Deque<List<E>> children = new ArrayDeque<>();
        children.push(new ArrayList<>());
        StringBuilder label = null;
        while (true) {
            int c = reader.read();

            if (c == '\t') {
                if (label != null) {
                    throw new ParseException("found tab in value on line", line);
                }

                indent++;
            } else if (c == -1 || c == '\n') {
                if (label == null && !(c == -1 && indent == 0)) {
                    throw new ParseException("empty line", line);
                }

                if (indent <= lastIndent) { // last was a leaf
                    if (!labels.isEmpty()) {
                        children.peek().add(factory.apply(labels.pop(), Collections.emptyList(), lines.pop()));
                    }
                } else { // this is a child of the previous node
                    if (indent != lastIndent + 1) {
                        throw new ParseException("indented too much", line);
                    }

                    children.push(new ArrayList<>());
                }

                while (lastIndent > indent) { // last node was ended
                    List<E> lastNodeChildren = children.pop();
                    children.peek().add(factory.apply(labels.pop(), lastNodeChildren, lines.pop()));
                    lastIndent--;
                }

                if (label != null) {
                    labels.push(label.toString());
                    lines.push(line);
                }

                lastIndent = indent;
                indent = 0;
                label = null;
                line++;
            } else if (c != '\r') {
                if (label == null) {
                    label = new StringBuilder();
                }

                label.appendCodePoint(c);
            }

            if (c == -1) {
                while (lastIndent > 0) { // last node was ended
                    List<E> lastNodeChildren = children.pop();
                    children.peek().add(factory.apply(labels.pop(), lastNodeChildren, lines.pop()));
                    lastIndent--;
                }

                return children.peek();
            }
        }
    }

    public static class ParseException extends Exception {
        public final int line;

        public ParseException(String message, int line) {
            super(message + " (line " + line + ")");
            this.line = line;
        }

        @Override
        public String toString() {
            return getClass().getName() + ": " + getMessage() + " (line " + line + ")";
        }
    }

    public interface Factory<E> {
        E apply(String label, List<E> children, int line) throws ParseException;
    }
}
