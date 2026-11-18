package vimulator.core;

/** Text transformations for ~, J/gJ and C-a/C-x. */
public final class Transforms {
    private Transforms() {}

    public static String toggleCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) sb.append(Character.toLowerCase(c));
            else if (Character.isLowerCase(c)) sb.append(Character.toUpperCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }

    public static String apply(Operator op, String s) {
        switch (op) {
            case TOGGLE_CASE: return toggleCase(s);
            case LOWERCASE: return s.toLowerCase();
            case UPPERCASE: return s.toUpperCase();
            default: throw new IllegalArgumentException(op.toString());
        }
    }

    /**
     * The decimal number at or after the caret on its line, including a directly preceding
     * '-'. @return {start, endExclusive} or null
     */
    public static int[] findNumber(TextModel t, int caret) {
        int line = Motions.lineOf(t, caret);
        int ls = t.lineStart(line), le = t.lineEnd(line);
        int i = caret;
        if (i < le && Character.isDigit(t.charAt(i))) {
            while (i > ls && Character.isDigit(t.charAt(i - 1))) i--;
        } else {
            while (i < le && !Character.isDigit(t.charAt(i))) i++;
            if (i >= le) return null;
        }
        int end = i;
        while (end < le && Character.isDigit(t.charAt(end))) end++;
        int start = i > ls && t.charAt(i - 1) == '-' ? i - 1 : i;
        return new int[] {start, end};
    }

    /**
     * Joins {@code line} with the next line.
     * @param spaces Vim's J: strip the next line's indent and insert one space where appropriate
     * @return the caret offset after the join, or -1 when there is no next line
     */
    public static int join(Editor ed, int line, boolean spaces) {
        if (line + 1 >= ed.lineCount()) return -1;
        int e = ed.lineEnd(line);
        if (!spaces) {
            ed.replace(e, e + 1, "");
            return e;
        }
        int p = e + 1, nextEnd = ed.lineEnd(line + 1);
        while (p < nextEnd && (ed.charAt(p) == ' ' || ed.charAt(p) == '\t')) p++;
        boolean lineEmpty = e == ed.lineStart(line);
        boolean endsBlank = !lineEmpty && (ed.charAt(e - 1) == ' ' || ed.charAt(e - 1) == '\t');
        boolean nextEmpty = p == nextEnd;
        boolean nextParen = !nextEmpty && ed.charAt(p) == ')';
        String sep = (lineEmpty || endsBlank || nextEmpty || nextParen) ? "" : " ";
        ed.replace(e, p, sep);
        return e;
    }
}
