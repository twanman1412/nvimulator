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

    // ---- words

    private static int cls(TextModel t, CharClass cc, int i, boolean big) {
        char c = t.charAt(i);
        return big ? cc.ofBig(c) : cc.of(c);
    }

    /** True when offset i is the position of an empty line. */
    static boolean emptyLineAt(TextModel t, int i) {
        int len = t.length();
        if (i > len) return false;
        boolean atLineEnd = i == len || t.charAt(i) == '\n';
        return atLineEnd && (i == 0 || t.charAt(i - 1) == '\n');
    }

    /** {@code w}/{@code W}: start of the count-th next word; may return length(). */
    public static int wordForward(TextModel t, CharClass cc, int caret, int count, boolean big) {
        int len = t.length();
        int i = caret;
        for (int n = 0; n < count; n++) {
            if (i >= len - 1) {
                if (n == 0) return -1;
                return len;
            }
            int start = i;
            int c0 = cls(t, cc, i, big);
            if (c0 != CharClass.BLANK) {
                while (i < len && cls(t, cc, i, big) == c0) i++;
            } else if (emptyLineAt(t, i)) {
                i++;
            }
            while (i < len && cls(t, cc, i, big) == CharClass.BLANK) {
                if (i > start && emptyLineAt(t, i)) break;
                i++;
            }
        }
        return i;
    }

    /** {@code b}/{@code B}: start of the count-th previous word. */
    public static int wordBackward(TextModel t, CharClass cc, int caret, int count, boolean big) {
        int i = caret;
        for (int n = 0; n < count; n++) {
            if (i == 0) {
                if (n == 0) return -1;
                break;
            }
            i--;
            while (i > 0 && cls(t, cc, i, big) == CharClass.BLANK && !emptyLineAt(t, i)) i--;
            if (emptyLineAt(t, i)) continue;
            int c0 = cls(t, cc, i, big);
            while (i > 0 && cls(t, cc, i - 1, big) == c0) i--;
        }
        return i;
    }

    /** {@code e}/{@code E}: end of the count-th next word. */
    public static int wordEnd(TextModel t, CharClass cc, int caret, int count, boolean big) {
        int len = t.length();
        int i = caret;
        for (int n = 0; n < count; n++) {
            if (i >= len - 1) {
                if (n == 0) return -1;
                break;
            }
            int j = i + 1;
            while (j < len && cls(t, cc, j, big) == CharClass.BLANK) j++;
            if (j >= len) {
                if (n == 0) return -1;
                break;
            }
            int c0 = cls(t, cc, j, big);
            while (j + 1 < len && cls(t, cc, j + 1, big) == c0) j++;
            i = j;
        }
        return i;
    }

    /** {@code ge}/{@code gE}: end of the count-th previous word; empty lines are stops. */
    public static int wordEndBackward(TextModel t, CharClass cc, int caret, int count, boolean big) {
        int i = caret;
        for (int n = 0; n < count; n++) {
            if (i == 0) {
                if (n == 0) return -1;
                break;
            }
            if (!emptyLineAt(t, i) && i < t.length()) {
                int c0 = cls(t, cc, i, big);
                if (c0 != CharClass.BLANK)
                    while (i > 0 && cls(t, cc, i - 1, big) == c0) i--;
            }
            if (i == 0) return 0;
            i--;
            while (i > 0 && cls(t, cc, i, big) == CharClass.BLANK && !emptyLineAt(t, i)) i--;
        }
        return i;
    }

    // ---- paragraphs

    private static boolean lineEmpty(TextModel t, int line) {
        return t.lineStart(line) == t.lineEnd(line);
    }

    /** {@code }}: next empty line; length() when there is none. */
    public static int paragraphForward(TextModel t, int caret, int count) {
        int last = t.lineCount() - 1;
        int line = lineOf(t, caret);
        for (int n = 0; n < count; n++) {
            while (line < last && lineEmpty(t, line)) line++;
            line++;
            while (line <= last && !lineEmpty(t, line)) line++;
            if (line > last) return t.length();
        }
        return t.lineStart(line);
    }

    /** {@code {}: previous empty line; 0 when there is none. */
    public static int paragraphBackward(TextModel t, int caret, int count) {
        int line = lineOf(t, caret);
        for (int n = 0; n < count; n++) {
            while (line > 0 && lineEmpty(t, line)) line--;
            line--;
            while (line >= 0 && !lineEmpty(t, line)) line--;
            if (line < 0) return 0;
        }
        return t.lineStart(line);
    }

    /** {@code gg}/{@code G}: line {@code line1} (1-based), clamped, first non-blank. */
    public static int goToLine(TextModel t, int line1) {
        int line = Math.max(0, Math.min(line1 - 1, t.lineCount() - 1));
        return firstNonBlank(t, line);
    }
}
