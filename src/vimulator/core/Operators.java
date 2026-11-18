package vimulator.core;

/** Applies an {@link Operator} to a {@link Range} on an {@link Editor}. */
public final class Operators {
    private Operators() {}

    /**
     * @param r a charwise range with an exclusive end ({@link MotionType#EXCLUSIVE}) or a
     *          {@link MotionType#LINEWISE} range; INCLUSIVE ranges are converted here
     * @return false when the operation failed (caller beeps)
     */
    public static boolean apply(Editor ed, Operator op, Range r, CharClass cc) {
        if (r.type() == MotionType.LINEWISE) return applyLinewise(ed, op, ed.lineOfOffset(r.start()), ed.lineOfOffset(r.end()));
        int start = r.start();
        int end = r.type() == MotionType.INCLUSIVE ? inclusiveEnd(ed, r.end()) : r.end();
        return applyCharwise(ed, op, start, Math.max(start, end));
    }

    /** Exclusive end for an inclusive end offset, never swallowing a line break. */
    static int inclusiveEnd(TextModel t, int end) {
        if (end >= t.length() || t.charAt(end) == '\n') return end;
        return end + 1;
    }

    private static boolean applyCharwise(Editor ed, Operator op, int start, int end) {
        String text = ed.text(start, end);
        switch (op) {
            case YANK:
                ed.yank(text, false);
                ed.setCaret(start);
                return true;
            case DELETE:
                if (end > start) {
                    ed.yank(text, false);
                    ed.replace(start, end, "");
                }
                ed.setCaret(start);
                return true;
            case CHANGE:
                if (end > start) {
                    ed.yank(text, false);
                    ed.replace(start, end, "");
                }
                ed.setCaret(start);
                ed.setMode(Mode.INSERT);
                return true;
            default:
                return false;
        }
    }

    /** Text of whole lines first..last, always ending in a newline. */
    static String linesText(TextModel t, Editor ed, int first, int last) {
        return ed.text(t.lineStart(first), t.lineEnd(last)) + "\n";
    }

    static String leadingWhitespace(Editor ed, int line) {
        int s = ed.lineStart(line), e = ed.lineEnd(line), i = s;
        while (i < e && (ed.charAt(i) == ' ' || ed.charAt(i) == '\t')) i++;
        return ed.text(s, i);
    }

    private static boolean applyLinewise(Editor ed, Operator op, int first, int last) {
        int lastLine = ed.lineCount() - 1;
        switch (op) {
            case YANK: {
                ed.yank(linesText(ed, ed, first, last), true);
                int caretLine = ed.lineOfOffset(ed.caret());
                if (first < caretLine) {
                    int col = Motions.column(ed, ed.caret());
                    ed.setCaret(Motions.atColumn(ed, first, col));
                }
                return true;
            }
            case DELETE: {
                ed.yank(linesText(ed, ed, first, last), true);
                if (last == lastLine && first > 0) {
                    ed.replace(ed.lineEnd(first - 1), ed.length(), "");
                } else {
                    ed.replace(ed.lineStart(first), Math.min(ed.lineEnd(last) + 1, ed.length()), "");
                }
                int line = Math.min(first, ed.lineCount() - 1);
                ed.setCaret(Motions.firstNonBlank(ed, line));
                return true;
            }
            case CHANGE: {
                ed.yank(linesText(ed, ed, first, last), true);
                String indent = leadingWhitespace(ed, first);
                int s = ed.lineStart(first);
                ed.replace(s, ed.lineEnd(last), indent);
                ed.setCaret(s + indent.length());
                ed.setMode(Mode.INSERT);
                return true;
            }
            default:
                return false;
        }
    }
}
