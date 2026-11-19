package nvimulator.core;

import static nvimulator.core.Fixture.*;

public class FindMotionTest {
    public static void testF() { check("|a,b,c", "f,", "a|,b,c"); check("|a,b,c", "2f,", "a,b|,c"); checkBeep("|a,b,c", "3f,"); check("a,b,|c", "F,", "a,b|,c"); check("|a,b,c", "2t,", "a,|b,c"); }
    public static void testRepeat() { check("|a,b,c,d", "f,;", "a,b|,c,d"); check("|a,b,c,d", "f,;,", "a|,b,c,d"); check("|ab,c,d", "t,;", "ab,|c,d"); }
    public static void testPercent() { check("|(a [b] c)", "%", "(a [b] c|)"); check("x |a (b)", "%", "x a (b|)"); check("{\n|}", "%", "|{\n}"); checkBeep("|abc", "%"); check("|" + "x\n".repeat(9) + "x", "50%", "x\n".repeat(4) + "|x\n" + "x\n".repeat(4) + "x"); }
    public static void testMarks() { check("ab|c\nd", "majmb`a", "ab|c\nd"); check("  ab|c\nd", "maj'a", "  |abc\nd"); checkBeep("|abc", "`q"); }
    public static void testEscCancelsCharArg() { Fixture f = of("|abc").keys("f<Esc>"); eq("|abc", f.render()); eq(0, f.beeps()); }
}
