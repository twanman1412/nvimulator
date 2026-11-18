package vimulator.core;

import static vimulator.core.Fixture.*;

import java.util.List;

public class OperatorTest {
    public static void testDw() { checkRegister("foo |bar baz", "dw", "foo |baz", "bar ", false); check("foo |bar\nbaz", "dw", "foo| \nbaz"); check("foo |bar baz", "d2w", "foo| "); check("|a b c d e f g", "2d3w", "|g"); }
    public static void testCwIsCe() { check("foo |bar baz", "cw", "foo | baz"); eq(Mode.INSERT, of("foo |bar baz").keys("cw").mode()); check("foo |bar baz", "de", "foo | baz"); }
    public static void testExclusiveRule() { check("one |two\n\nthree", "d}", "one| \n\nthree"); check("|one two\n\nthree", "d}", "|\nthree"); }
    public static void testLinewise() { checkRegister("|foo\nbar", "dd", "|bar", "foo\n", true); check("foo\n|bar", "dd", "|foo"); check("  a\n|b\n  c", "dd", "  a\n  |c"); check("|a\nb\nc", "5dd", "|"); check("a\n|b\nc", "dk", "|c"); check("|a\nb\nc", "dj", "|c"); checkBeep("|a\nb", "dk"); checkBeep("a\n|b", "dj"); check("a\n|b\nc", "dG", "|a"); check("a\n|b\nc", "dgg", "|c"); }
    public static void testFindOps() { check("foo(|bar)", "dt)", "foo(|)"); check("foo(|bar)", "df)", "foo|("); }
    public static void testChangeAndYank() { check("  |foo", "cc", "  |"); check("a\n|  b\nc\nd", "cj", "a\n  |\nd"); checkRegister("foo\n|bar\nbaz", "yy", "foo\n|bar\nbaz", "bar\n", true); checkRegister("foo |bar", "yb", "|foo bar", "foo ", false); }
    public static void testAliases() { check("ab|c", "x", "a|b"); check("a|bc", "5x", "|a"); checkBeep("|abc", "X"); check("ab|c", "X", "a|c"); check("a|bc def", "D", "|a"); check("a|bc", "C", "a|"); check("a|bc", "s", "a|c"); check("  |ab", "S", "  |"); checkRegister("|ab", "Y", "|ab", "ab\n", true); }
    public static void testInvalidAfterOperatorCancels() { Fixture f = of("|abc").keys("dz"); eq("|abc", f.render()); eq(1, f.beeps()); eq(Engine.Result.DONE, f.lastResult()); eq("|bc", f.keys("x").render()); }
    public static void testLastLineEdges() { check("a\n|b", "dd", "|a"); check("a\nb\n|", "dd", "a\n|b"); checkBeep("a\n|b", "dj"); check("a\n|b\n", "dG", "|a"); }
    public static void testOneUndoUnitPerCommand() { Fixture f = of("|a b c").keys("d2w"); eq(0, f.editor().editDepth); }
    public static void testNothingThrowsOnEmptyInput() {
        for (String k : List.of("h","l","j","k","w","b","e","ge","W","B","E","0","^","$","{","}","%","G","gg","dd","dw","cw","yy","x","X","D","C","s","S","de","d}","dG"))
            for (String s : List.of("|", "a\n|\nb")) {
                try {
                    of(s).keys(k);
                } catch (RuntimeException e) {
                    throw new AssertionError("[" + show(s) + "] " + k + " threw " + e, e);
                }
            }
    }
}
