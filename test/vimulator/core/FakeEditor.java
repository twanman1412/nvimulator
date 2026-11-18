package vimulator.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-memory Editor used by tests. */
public class FakeEditor implements Editor {
    final StringBuilder text;
    int caret;
    Mode mode = Mode.NORMAL;
    int selAnchor = -1, selCaret = -1;
    Mode selMode;
    String register = null;
    boolean registerLinewise;
    int beeps;
    int firstVisible = 0, height = 10;
    int[] lastReindent;
    Operator lastBlockOperator;
    String lastSearch;
    int editDepth;
    boolean editable = true;

    public FakeEditor(String text, int caret) {
        this.text = new StringBuilder(text);
        this.caret = caret;
    }

    // --- TextModel
    @Override public int length() { return text.length(); }
    @Override public char charAt(int offset) { return text.charAt(offset); }

    @Override public int lineCount() {
        int n = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') n++;
        return n;
    }

    @Override public int lineOfOffset(int offset) {
        int line = 0;
        for (int i = 0; i < offset && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    @Override public int lineStart(int line) {
        if (line == 0) return 0;
        int seen = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n' && ++seen == line) return i + 1;
        }
        throw new IndexOutOfBoundsException("line " + line);
    }

    @Override public int lineEnd(int line) {
        int i = text.indexOf("\n", lineStart(line));
        return i < 0 ? text.length() : i;
    }

    // --- Editor
    @Override public int caret() { return caret; }

    @Override public void setCaret(int offset) {
        if (offset < 0 || offset > text.length())
            throw new IndexOutOfBoundsException("caret " + offset + " in length " + text.length());
        // like Vimulator's caret listener in jEdit: in normal mode the caret can't sit on a line end
        if (mode == Mode.NORMAL) offset = Motions.clampNormal(this, offset);
        caret = offset;
        int line = lineOfOffset(offset);
        if (line < firstVisible) firstVisible = line;
        else if (line > firstVisible + height - 1) firstVisible = line - height + 1;
    }

    @Override public Mode mode() { return mode; }

    @Override public void setMode(Mode m) {
        if ((mode == Mode.INSERT || mode == Mode.REPLACE) && m != Mode.INSERT && m != Mode.REPLACE) {
            if (caret > lineStart(lineOfOffset(caret))) caret--;
        }
        if (!m.isVisual()) { selAnchor = selCaret = -1; selMode = null; }
        mode = m;
    }

    @Override public void setSelection(int anchor, int caret, Mode visualMode) {
        selAnchor = anchor; selCaret = caret; selMode = visualMode;
    }

    @Override public String text(int start, int end) { return text.substring(start, end); }
    @Override public void replace(int start, int end, String s) { text.replace(start, end, s); }
    @Override public void yank(String s, boolean linewise) { register = s; registerLinewise = linewise; }

    @Override public void shiftLines(int firstLine, int lastLine, int direction) {
        for (int l = firstLine; l <= lastLine; l++) {
            int s = lineStart(l);
            if (direction > 0) {
                if (lineEnd(l) > s) text.insert(s, "    ");
            } else {
                int n = 0;
                while (n < 4 && s + n < text.length() && text.charAt(s + n) == ' ') n++;
                text.delete(s, s + n);
            }
        }
    }

    @Override public void reindentLines(int firstLine, int lastLine) { lastReindent = new int[] {firstLine, lastLine}; }
    @Override public void blockOperator(Operator op) { lastBlockOperator = op; }

    @Override public void search(String javaRegex, boolean backward) {
        lastSearch = javaRegex;
        Matcher m = Pattern.compile(javaRegex).matcher(text);
        int found = -1;
        if (!backward) {
            if (m.find(Math.min(caret + 1, text.length()))) found = m.start();
            else if (m.find(0)) found = m.start();
        } else {
            int last = -1, lastAny = -1;
            m.reset();
            while (m.find()) {
                if (m.start() < caret) last = m.start();
                lastAny = m.start();
            }
            found = last >= 0 ? last : lastAny;
        }
        if (found < 0) beep(); else setCaret(found);
    }

    @Override public void beginEdit() { editDepth++; }
    @Override public void endEdit() { editDepth--; }
    @Override public void beep() { beeps++; }
    @Override public String extraWordChars() { return ""; }
    @Override public boolean editable() { return editable; }
    @Override public int firstVisibleLine() { return firstVisible; }
    @Override public int visibleLineCount() { return height; }
    @Override public void setFirstVisibleLine(int line) { firstVisible = line; }

    /** Inclusive text of the current visual selection, or null. */
    String selectedText() {
        if (selMode == null) return null;
        int a = Math.min(selAnchor, selCaret), b = Math.max(selAnchor, selCaret);
        if (selMode == Mode.VISUAL_LINE) {
            int s = lineStart(lineOfOffset(a)), e = lineEnd(lineOfOffset(b));
            return text.substring(s, Math.min(e + 1, text.length()));
        }
        return text.substring(a, Math.min(b + 1, text.length()));
    }
}
