package vimulator.core;

import static vimulator.core.Fixture.*;

public class TextModelTest {
    public static void testLines() {
        FakeEditor e = Fixture.of("ab\n|\ncd").editor();
        eq(3, e.lineCount()); eq(0, e.lineStart(0)); eq(2, e.lineEnd(0));
        eq(3, e.lineStart(1)); eq(3, e.lineEnd(1)); eq(6, e.lineEnd(2));
        eq(1, e.lineOfOffset(3)); eq(2, e.lineOfOffset(6)); eq(3, e.caret());
    }

    public static void testRenderRoundTrip() { eq("ab\n|\ncd", Fixture.of("ab\n|\ncd").render()); }

    public static void testCharClass() {
        CharClass c = new CharClass("");
        eq(CharClass.WORD, c.of('_')); eq(CharClass.PUNCT, c.of('.')); eq(CharClass.BLANK, c.of('\t'));
        eq(CharClass.WORD, new CharClass(".").of('.')); eq(CharClass.WORD, c.ofBig('.'));
    }

    public static void testEmptyBuffer() {
        FakeEditor e = Fixture.of("|").editor();
        eq(1, e.lineCount()); eq(0, e.lineEnd(0));
    }
}
