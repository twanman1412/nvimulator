package vimulator.core;

import static vimulator.core.Fixture.*;

public class TransformTest {
    public static void testCaseOps() { check("|hello world", "g~w", "|HELLO world"); check("|HeLLo", "guu", "|hello"); check("|HeLLo", "gUU", "|HELLO"); check("|HeLLo", "gUgU", "|HELLO"); check("|ab", "g~~", "|AB"); check("a |bc d", "gUiw", "a |BC d"); check("|ab", "~", "A|b"); check("|abc", "5~", "AB|C"); }
    public static void testShift() { check("|a", ">>", "    |a"); check("|a\nb", ">j", "    |a\n    b"); check("|a\nb\nc", "2>>", "    |a\n    b\nc"); check("      |a", "<<", "  |a"); int[] r = of("|a\nb").keys("=j").lastReindent(); eq(0, r[0]); eq(1, r[1]); int[] r2 = of("a\n|b").keys("==").lastReindent(); eq(1, r2[0]); eq(1, r2[1]); }
    public static void testJoin() { check("|a\n  b", "J", "a| b"); check("|a\n)b", "J", "a|)b"); check("|a \nb", "J", "a |b"); check("|a\nb\nc", "3J", "a b| c"); check("|a\n  b", "gJ", "a|  b"); checkBeep("|a", "J"); }
    public static void testIncrement() { check("x |9 y", "<C-a>", "x 1|0 y"); check("|foo 41", "<C-a>", "foo 4|2"); check("x -|1", "<C-a>", "x |0"); check("|1", "5<C-a>", "|6"); check("|0", "<C-x>", "-|1"); checkBeep("|abc", "<C-a>"); }
    public static void testReplace() { check("|abc", "rx", "|xbc"); check("|abc", "2rx", "x|xc"); checkBeep("|ab", "3rx"); }
    public static void testToggleCaseHelper() { eq("aB1", Transforms.toggleCase("Ab1")); }
}
