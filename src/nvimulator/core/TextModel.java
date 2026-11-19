package nvimulator.core;

/**
 * Read-only line/offset view of a text buffer.
 * Lines are separated by '\n'. {@link #lineEnd(int)} is the offset of the
 * terminating '\n', or {@link #length()} for the last line.
 */
public interface TextModel {
    int length();
    char charAt(int offset);
    int lineCount();
    /** Line containing offset; {@code lineOfOffset(length())} is the last line. */
    int lineOfOffset(int offset);
    int lineStart(int line);
    int lineEnd(int line);
}
