package vimulator.core;

import static vimulator.core.Fixture.*;

public class BasicMotionTest {
    public static void testHL() { check("a|bc", "l", "ab|c"); check("a|bc", "5l", "ab|c"); checkBeep("ab|c", "l"); check("ab|c", "2h", "|abc"); checkBeep("|abc", "h"); }
    public static void testJKDesiredColumn() { check("ab|cdef\nx\nabcdef", "jj", "abcdef\nx\nab|cdef"); check("|ab\nabcdef", "$j", "ab\nabcde|f"); checkBeep("|a", "j"); }
    public static void testLineEnds() { check("  a|bc", "0", "|  abc"); check("  a|bc", "^", "  |abc"); check("|ab\ncd", "2$", "ab\nc|d"); }
    public static void testLineMotions() { check("a\n|b\n  c", "+", "a\nb\n  |c"); check("  a\n|b", "-", "  |a\nb"); check("a\nb\n|  c", "gg", "|a\nb\n  c"); check("|a\nb\n  c", "G", "a\nb\n  |c"); check("|a\nb\nc", "2G", "a\n|b\nc"); check("|a\nb\nc", "2gg", "a\n|b\nc"); }
    public static void testZeroIsMotionUnlessCounting() { check("ab|c\n" + "x\n".repeat(12), "10j", "abc\n" + "x\n".repeat(9) + "|x\n" + "x\n".repeat(2)); check("ab|c", "0", "|abc"); }
    public static void testHugeCount() { checkBeep("|a\nb", "99999999999j"); }
    public static void testUnknownPrefixRejected() { checkRejected("|a", "p"); checkRejected("|a", "3p"); checkRejected("|a", "zf"); checkRejected("|a", "<C-w>"); }
}
