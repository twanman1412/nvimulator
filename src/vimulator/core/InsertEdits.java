package vimulator.core;

/** Ranges deleted by insert-mode C-w and C-u. Both return the start of [start, caret). */
public final class InsertEdits {
    private InsertEdits() {}

    /** C-w: blanks before the caret, then one run of the class of the char before them. */
    public static int deleteWordStart(TextModel t, CharClass cc, int caret) {
        if (caret <= 0) return 0;
        int ls = t.lineStart(Motions.lineOf(t, caret));
        if (caret == ls) return caret - 1;
        int i = caret;
        while (i > ls && isBlank(t.charAt(i - 1))) i--;
        if (i > ls) {
            int c0 = cc.of(t.charAt(i - 1));
            while (i > ls && cc.of(t.charAt(i - 1)) == c0) i--;
        }
        return i;
    }

    /** C-u: back to the first non-blank, then to the line start, then join with the previous line. */
    public static int deleteLineStart(TextModel t, int caret) {
        if (caret <= 0) return 0;
        int line = Motions.lineOf(t, caret);
        int ls = t.lineStart(line);
        if (caret == ls) return caret - 1;
        int i = ls, le = t.lineEnd(line);
        while (i < le && isBlank(t.charAt(i))) i++;
        return caret > i ? i : ls;
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t';
    }
}
