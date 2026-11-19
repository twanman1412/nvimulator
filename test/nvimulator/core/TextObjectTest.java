package nvimulator.core;

import static nvimulator.core.Fixture.*;

import java.util.List;

public class TextObjectTest {
    public static void testWord() { check("foo b|ar baz", "diw", "foo | baz"); check("foo b|ar baz", "daw", "foo |baz"); check("foo |bar", "daw", "fo|o"); check("foo  | bar", "diw", "foo|bar"); check("a foo.b|ar b", "diW", "a | b"); check("foo b|ar baz", "ciw", "foo | baz"); }
    public static void testQuotes() { check("x = \"he|llo\" y", "di\"", "x = \"|\" y"); check("x = \"he|llo\" y", "da\"", "x = |y"); check("|x = \"hello\"", "di\"", "x = \"|\""); check("\"a\\\"|b\"", "di\"", "\"|\""); check("'a|b'", "di'", "'|'"); check("`a|b`", "di`", "`|`"); checkBeep("|abc", "di\""); }
    public static void testBrackets() { check("f(a, |b)", "di(", "f(|)"); check("f(a, |b)", "dib", "f(|)"); check("f(a, |b)", "da(", "|f"); check("f|(a)", "di(", "f(|)"); check("((a|b))", "2di(", "(|)"); check("x[|1]", "di[", "x[|]"); check("<|a>", "di<", "<|>"); check("{|a}", "diB", "{|}"); check("f(|)", "di(", "f(|)"); checkBeep("|a (b)", "di("); }
    public static void testMultilineBraces() { check("if {\n  |foo\n}", "di{", "if {\n|}"); Fixture f = of("if {\n  |foo\n}").keys("ci{"); eq("if {\n  |\n}", f.render()); eq(Mode.INSERT, f.mode()); }
    public static void testParagraph() { check("a\n|b\n\nc", "dip", "|\nc"); check("a\n|b\n\nc", "dap", "|c"); check("a\n\n|b", "dap", "|a"); }
    public static void testTags() { check("<a><b>|x</b></a>", "dit", "<a><b>|</b></a>"); check("<a><b>|x</b></a>", "dat", "<a>|</a>"); check("<a><b>|x</b></a>", "2dit", "<a>|</a>"); check("<div class=\"x\">h|i</div>", "dit", "<div class=\"x\">|</div>"); }
    public static void testUnbalanced() { checkBeep("f(|a", "di("); checkBeep("\"a|b", "di\""); checkBeep("a)|b(", "di("); }
    public static void testTextObjectsOnEmptyInput() {
        for (String k : List.of("diw", "daw", "di\"", "di(", "dip", "dap", "dit"))
            for (String s : List.of("|", "a\n|\nb")) {
                try {
                    of(s).keys(k);
                } catch (RuntimeException e) {
                    throw new AssertionError("[" + show(s) + "] " + k + " threw " + e, e);
                }
            }
    }
}
