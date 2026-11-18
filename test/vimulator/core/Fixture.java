package vimulator.core;

import java.util.Objects;

/** Test fixture: "foo |bar" notation ('|' before the caret char), key driving and assertions. */
public class Fixture {
    final FakeEditor ed;

    private Fixture(FakeEditor ed) { this.ed = ed; }

    public static Fixture of(String textWithCaret) {
        int c = textWithCaret.indexOf('|');
        if (c < 0 || textWithCaret.indexOf('|', c + 1) >= 0)
            throw new IllegalArgumentException("need exactly one '|': " + textWithCaret);
        String text = textWithCaret.substring(0, c) + textWithCaret.substring(c + 1);
        return new Fixture(new FakeEditor(text, c));
    }

    public static Fixture lines(int n) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < n; i++) sb.append(i == 0 ? "" : "\n").append("l").append(i);
        return of(sb.toString());
    }

    public Fixture viewport(int firstLine, int height) { ed.firstVisible = firstLine; ed.height = height; return this; }
    public Fixture caretLine(int line) { ed.setCaret(ed.lineStart(line)); return this; }

    public FakeEditor editor() { return ed; }

    public String render() {
        return ed.text.substring(0, ed.caret) + "|" + ed.text.substring(ed.caret);
    }

    public int beeps() { return ed.beeps; }
    public Mode mode() { return ed.mode; }
    public String register() { return ed.register; }
    public boolean registerLinewise() { return ed.registerLinewise; }
    public String selectedText() { return ed.selectedText(); }
    public int firstVisibleLine() { return ed.firstVisible; }
    public int caretLine() { return ed.lineOfOffset(ed.caret); }
    public Operator lastBlockOperator() { return ed.lastBlockOperator; }
    public int[] lastReindent() { return ed.lastReindent; }
    public String lastSearch() { return ed.lastSearch; }

    // --- assertions
    public static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected, actual))
            throw new AssertionError("expected <" + show(expected) + "> but was <" + show(actual) + ">");
    }

    public static void assertTrue(String msg, boolean cond) {
        if (!cond) throw new AssertionError(msg);
    }

    static String show(Object o) {
        return o == null ? "null" : o.toString().replace("\n", "\\n").replace("\t", "\\t");
    }
}
