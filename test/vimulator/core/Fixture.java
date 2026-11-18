package vimulator.core;

import java.util.Objects;

/** Test fixture: "foo |bar" notation ('|' before the caret char), key driving and assertions. */
public class Fixture {
    final FakeEditor ed;
    final Engine engine;
    Engine.Result lastResult;
    boolean allowReject;

    private Fixture(FakeEditor ed) { this.ed = ed; this.engine = new Engine(ed); }

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
    public Engine engine() { return engine; }
    public Engine.Result lastResult() { return lastResult; }

    /** Feeds keys: "<Name>" tokens or single characters; "<lt>" is a literal '<'. */
    public Fixture keys(String keys) {
        for (String tok : tokenize(keys)) {
            if (ed.mode == Mode.INSERT || ed.mode == Mode.REPLACE) {
                insertKey(tok);
                continue;
            }
            lastResult = engine.feed(tok);
            if (lastResult == Engine.Result.REJECTED) {
                if (tok.equals("<Esc>")) {
                    if (ed.mode.isVisual()) ed.setMode(Mode.NORMAL);
                } else if (!allowReject) {
                    throw new AssertionError("rejected: " + tok + " in \"" + show(keys) + "\"");
                }
            }
        }
        return this;
    }

    private void insertKey(String tok) {
        switch (tok) {
            case "<Esc>": engine.onInsertExit(); ed.setMode(Mode.NORMAL); return;
            case "<BS>": if (ed.caret > 0) { ed.text.deleteCharAt(ed.caret - 1); ed.caret--; } return;
            default:
                String s = tok.equals("<Space>") ? " " : tok.equals("<CR>") ? "\n" : tok.equals("<Tab>") ? "\t"
                        : tok.equals("<lt>") ? "<" : tok;
                if (s.length() != 1) throw new AssertionError("unsupported insert key " + tok);
                ed.text.insert(ed.caret, s);
                ed.caret++;
        }
    }

    static java.util.List<String> tokenize(String keys) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < keys.length(); i++) {
            char c = keys.charAt(i);
            int close = c == '<' ? keys.indexOf('>', i) : -1;
            if (close > i + 1) {
                String name = keys.substring(i, close + 1);
                out.add(name.equals("<lt>") ? "<" : name);
                i = close;
            } else {
                out.add(String.valueOf(c));
            }
        }
        return out;
    }

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
    public static void check(String before, String keys, String after) {
        Fixture f;
        try {
            f = of(before).keys(keys);
        } catch (AssertionError e) {
            throw new AssertionError(ctx(before, keys) + e.getMessage());
        } catch (RuntimeException e) {
            throw new AssertionError(ctx(before, keys) + "threw " + e, e);
        }
        if (!after.equals(f.render()))
            throw new AssertionError(ctx(before, keys) + "expected <" + show(after) + "> but was <" + show(f.render()) + ">");
    }

    public static void checkRegister(String before, String keys, String after, String reg, boolean linewise) {
        check(before, keys, after);
        Fixture f = of(before).keys(keys);
        if (!Objects.equals(reg, f.register()) || linewise != f.registerLinewise())
            throw new AssertionError(ctx(before, keys) + "register expected <" + show(reg) + "," + linewise
                    + "> but was <" + show(f.register()) + "," + f.registerLinewise() + ">");
    }

    public static void checkRejected(String before, String keys) {
        Fixture f = of(before);
        f.allowReject = true;
        f.keys(keys);
        if (f.lastResult != Engine.Result.REJECTED)
            throw new AssertionError(ctx(before, keys) + "expected REJECTED but was " + f.lastResult);
        if (!f.ed.text.toString().equals(of(before).ed.text.toString()))
            throw new AssertionError(ctx(before, keys) + "text changed to <" + show(f.render()) + ">");
    }

    public static void checkBeep(String before, String keys) {
        Fixture f;
        try {
            f = of(before).keys(keys);
        } catch (RuntimeException e) {
            throw new AssertionError(ctx(before, keys) + "threw " + e, e);
        }
        if (!before.equals(f.render()))
            throw new AssertionError(ctx(before, keys) + "expected unchanged <" + show(before) + "> but was <" + show(f.render()) + ">");
        if (f.beeps() < 1) throw new AssertionError(ctx(before, keys) + "expected a beep");
    }

    static String ctx(String before, String keys) { return "[" + show(before) + "] " + show(keys) + ": "; }

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
