package vimulator.core;

import static vimulator.core.Fixture.*;

public class InsertEditsTest {
    static final CharClass cc = new CharClass("");

    static TextModel m(String s) {
        return Fixture.of(s).editor();
    }

    public static void testCtrlW() { eq(4, InsertEdits.deleteWordStart(m("foo bar|"), cc, 7)); eq(4, InsertEdits.deleteWordStart(m("foo bar  |"), cc, 9)); eq(7, InsertEdits.deleteWordStart(m("foo.bar..|"), cc, 9)); eq(3, InsertEdits.deleteWordStart(m("foo\n|x"), cc, 4)); eq(0, InsertEdits.deleteWordStart(m("|"), cc, 0)); }
    public static void testCtrlU() { eq(2, InsertEdits.deleteLineStart(m("  ab|"), 4)); eq(0, InsertEdits.deleteLineStart(m("  |ab"), 2)); eq(1, InsertEdits.deleteLineStart(m("a\n|b"), 2)); eq(0, InsertEdits.deleteLineStart(m("|"), 0)); }
}
