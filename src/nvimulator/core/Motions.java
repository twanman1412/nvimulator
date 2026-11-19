package nvimulator.core;

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

    /**
     * The last real line. A buffer ending in '\n' has an empty phantom line after it (jEdit
     * shows it); Vim has no such line, so motions and line counts stop before it.
     */
    public static int lastLine(TextModel t) {
        int n = t.lineCount() - 1;
        if (n > 0 && t.lineStart(n) == t.length()) return n - 1;
        return n;
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

    /** Line {@code count} lines below the caret line, clamped; -1 when already on the last line. */
    private static int lineBelow(TextModel t, int caret, int count) {
        int cur = lineOf(t, caret), last = lastLine(t);
        if (cur >= last) return -1;
        return (int) Math.min((long) cur + count, last);
    }

    /** Line {@code count} lines above the caret line, clamped; -1 when already on the first line. */
    private static int lineAbove(TextModel t, int caret, int count) {
        int cur = lineOf(t, caret);
        if (cur <= 0) return -1;
        return (int) Math.max((long) cur - count, 0);
    }

    /** {@code $}: last char of the line count-1 lines down (clamped to the last line). */
    public static int lineEnd(TextModel t, int caret, int count) {
        int cur = lineOf(t, caret);
        int line = (int) Math.min((long) cur + count - 1, Math.max(cur, lastLine(t)));
        return lastChar(t, line);
    }

    // ---- vertical

    public static int down(TextModel t, int caret, int count, int desiredCol) {
        int line = lineBelow(t, caret, count);
        return line < 0 ? -1 : atColumn(t, line, desiredCol);
    }

    public static int up(TextModel t, int caret, int count, int desiredCol) {
        int line = lineAbove(t, caret, count);
        return line < 0 ? -1 : atColumn(t, line, desiredCol);
    }

    /** {@code +} / {@code <CR>}: first non-blank count lines down. */
    public static int nextLineStart(TextModel t, int caret, int count) {
        int line = lineBelow(t, caret, count);
        return line < 0 ? -1 : firstNonBlank(t, line);
    }

    /** {@code -}: first non-blank count lines up. */
    public static int prevLineStart(TextModel t, int caret, int count) {
        int line = lineAbove(t, caret, count);
        return line < 0 ? -1 : firstNonBlank(t, line);
    }

    /** {@code _}: first non-blank count-1 lines down (clamped). */
    public static int underscore(TextModel t, int caret, int count) {
        int cur = lineOf(t, caret);
        int line = (int) Math.min((long) cur + count - 1, Math.max(cur, lastLine(t)));
        return firstNonBlank(t, line);
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

    // ---- find char (line-limited)

    /**
     * {@code f t F T}. When {@code repeat} is true and the motion is a till ({@code t/T}), a
     * target char directly next to the caret is skipped, so {@code ;} makes progress.
     */
    public static int findChar(TextModel t, int caret, int count, char ch, boolean backward,
                               boolean till, boolean repeat) {
        int line = lineOf(t, caret);
        int ls = t.lineStart(line), le = t.lineEnd(line);
        int step = backward ? -1 : 1;
        int i = caret + step;
        if (till && repeat && i >= ls && i < le && t.charAt(i) == ch) i += step;
        int found = -1;
        for (int n = 0; n < count; n++) {
            found = -1;
            for (; i >= ls && i < le; i += step) {
                if (t.charAt(i) == ch) {
                    found = i;
                    i += step;
                    break;
                }
            }
            if (found < 0) return -1;
        }
        return till ? found - step : found;
    }

    // ---- brackets

    private static final String OPEN = "([{", CLOSE = ")]}";

    /** {@code %}: the bracket at or after the caret on the line, then its match. */
    public static int matchBracket(TextModel t, int caret) {
        int le = t.lineEnd(lineOf(t, caret));
        int i = caret;
        while (i < le && OPEN.indexOf(t.charAt(i)) < 0 && CLOSE.indexOf(t.charAt(i)) < 0) i++;
        if (i >= le) return -1;
        char c = t.charAt(i);
        int k = OPEN.indexOf(c);
        if (k >= 0) return scanForward(t, i, OPEN.charAt(k), CLOSE.charAt(k));
        k = CLOSE.indexOf(c);
        return scanBackward(t, i, OPEN.charAt(k), CLOSE.charAt(k));
    }

    /** From an opening bracket at {@code from}, the offset of its closing bracket, or -1. */
    static int scanForward(TextModel t, int from, char open, char close) {
        int depth = 0;
        for (int i = from; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == open) depth++;
            else if (c == close && --depth == 0) return i;
        }
        return -1;
    }

    /** From a closing bracket at {@code from}, the offset of its opening bracket, or -1. */
    static int scanBackward(TextModel t, int from, char open, char close) {
        int depth = 0;
        for (int i = from; i >= 0; i--) {
            char c = t.charAt(i);
            if (c == close) depth++;
            else if (c == open && --depth == 0) return i;
        }
        return -1;
    }

    /** {@code N%}: first non-blank of the line N percent into the buffer. */
    public static int percentLine(TextModel t, int percent) {
        if (percent > 100) return -1;
        int line1 = (int) (((long) percent * (lastLine(t) + 1) + 99) / 100);
        return goToLine(t, Math.max(1, line1));
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
        int line = Math.max(0, Math.min(line1 - 1, lastLine(t)));
        return firstNonBlank(t, line);
    }
}
