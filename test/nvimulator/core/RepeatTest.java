package nvimulator.core;

import static nvimulator.core.Fixture.*;

public class RepeatTest {
    public static void testDotOperator() { check("|a b c d", "dw.", "|c d"); check("|a b c d", "dw2.", "|d"); check("|abc", "x..", "|"); check("|ab", "dlyl.", "|"); check("|1", "<C-a>.", "|3"); }
    public static void testDotChange() { check("|foo foo", "cwbar<Esc>w.", "bar ba|r"); check("|a b", "ciwx<Esc>w.", "x |x"); }
    public static void testInsertCommands() { check("|a\nb", "Ax<Esc>j.", "ax\nb|x"); check("|a", "3ix<Esc>", "xx|xa"); check("|a\nb", "ox<Esc>.", "a\nx\n|x\nb"); check("  |a", "Ox<Esc>", "  |x\n  a"); check("  a|b", "Ix<Esc>", "  |xab"); check("a|b", "ax<Esc>", "ab|x"); check("|ab", "ix<Esc>", "|xab"); }
    public static void testDotNothing() { checkBeep("|a", "."); }
    public static void testDotIsOneUndoUnit() { Fixture f = of("|foo foo").keys("cwbar<Esc>w."); eq(0, f.editor().editDepth); }
}
