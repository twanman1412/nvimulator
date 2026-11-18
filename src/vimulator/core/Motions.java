package vimulator.core;

/**
 * Pure motion functions. Each returns a target offset, or -1 when the motion fails.
 * Offsets may equal {@code t.length()} to mean "end of buffer" (used by operators).
 */
public final class Motions {
    private Motions() {}

    // ---- line helpers

    public static int lineOf(TextModel t, int offset) {
        return t.lineOfOffset(Math.min(offset, t.length()));
    }

    /** Offset of the last character on the line, or the line start if the line is empty. */
    public static int lastChar(TextModel t, int line) {
        int s = t.lineStart(line), e = t.lineEnd(line);
        return e > s ? e - 1 : s;
    }

    public static int column(TextModel t, int offset) {
        return offset - t.lineStart(lineOf(t, offset));
    }

    /** First non-blank of the line; the last char if the line is all blank. */
    public static int firstNonBlank(TextModel t, int line) {
        int s = t.lineStart(line), e = t.lineEnd(line);
        for (int i = s; i < e; i++) {
            char c = t.charAt(i);
            if (c != ' ' && c != '\t') return i;
        }
        return lastChar(t, line);
    }

    /** Clamps an offset to a valid normal-mode caret position (on a character, not on '\n'). */
    public static int clampNormal(TextModel t, int offset) {
        offset = Math.max(0, Math.min(offset, t.length()));
        int line = t.lineOfOffset(offset);
        return Math.min(offset, lastChar(t, line));
    }

    static int atColumn(TextModel t, int line, int col) {
        int s = t.lineStart(line);
        return s + Math.min(col, lastChar(t, line) - s);
    }

    // ---- horizontal

    public static int left(TextModel t, int caret, int count) {
        int s = t.lineStart(lineOf(t, caret));
        if (caret <= s) return -1;
        return Math.max(s, caret - count);
    }

    /**
     * @param forOperator when true the motion may reach the end of the line (one past the last
     *                    char), so that {@code dl} / {@code x} can delete the last character.
     */
    public static int right(TextModel t, int caret, int count, boolean forOperator) {
        int line = lineOf(t, caret);
        int limit = forOperator ? t.lineEnd(line) : lastChar(t, line);
        if (caret >= limit) return -1;
        return (int) Math.min((long) limit, (long) caret + count);
    }

    public static int lineHome(TextModel t, int caret) {
        return t.lineStart(lineOf(t, caret));
    }

    public static int firstNonBlank(TextModel t, int caret, int count) {
        return firstNonBlank(t, lineOf(t, caret));
    }

    /** {@code $}: last char of the line count-1 lines down. */
    public static int lineEnd(TextModel t, int caret, int count) {
        long line = (long) lineOf(t, caret) + count - 1;
        if (line >= t.lineCount()) return -1;
        return lastChar(t, (int) line);
    }

    // ---- vertical

    public static int down(TextModel t, int caret, int count, int desiredCol) {
        long line = (long) lineOf(t, caret) + count;
        if (line >= t.lineCount()) return -1;
        return atColumn(t, (int) line, desiredCol);
    }

    public static int up(TextModel t, int caret, int count, int desiredCol) {
        long line = (long) lineOf(t, caret) - count;
        if (line < 0) return -1;
        return atColumn(t, (int) line, desiredCol);
    }

    /** {@code +} / {@code <CR>}: first non-blank count lines down. */
    public static int nextLineStart(TextModel t, int caret, int count) {
        long line = (long) lineOf(t, caret) + count;
        if (line >= t.lineCount()) return -1;
        return firstNonBlank(t, (int) line);
    }

    /** {@code -}: first non-blank count lines up. */
    public static int prevLineStart(TextModel t, int caret, int count) {
        long line = (long) lineOf(t, caret) - count;
        if (line < 0) return -1;
        return firstNonBlank(t, (int) line);
    }

    /** {@code _}: first non-blank count-1 lines down. */
    public static int underscore(TextModel t, int caret, int count) {
        long line = (long) lineOf(t, caret) + count - 1;
        if (line >= t.lineCount()) return -1;
        return firstNonBlank(t, (int) line);
    }

    /** {@code gg}/{@code G}: line {@code line1} (1-based), clamped, first non-blank. */
    public static int goToLine(TextModel t, int line1) {
        int line = Math.max(0, Math.min(line1 - 1, t.lineCount() - 1));
        return firstNonBlank(t, line);
    }
}
