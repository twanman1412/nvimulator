package nvimulator.core;

import static nvimulator.core.Fixture.*;

public class VisualTest {
    public static void testCharwise() { check("|foo bar", "vlld", "| bar"); eq("foo bar", of("|foo bar").keys("vee").selectedText()); check("foo b|ar baz", "viwd", "foo | baz"); checkRegister("|ab", "vly", "|ab", "ab", false); eq(Mode.NORMAL, of("|ab").keys("vly").mode()); }
    public static void testLinewise() { check("|a\nb\nc", "Vjd", "|c"); check("|a\nb", "Vj>", "    |a\n    b"); Fixture f = of("a\n|b\nc").keys("Vc"); eq("a\n|\nc", f.render()); eq(Mode.INSERT, f.mode()); check("|a\nb\nc", "VjJ", "a| b\nc"); eq("a\nb\n", of("|a\nb\nc").keys("Vj").selectedText()); }
    public static void testCase() { check("|abc", "vl~", "|ABc"); check("|abc", "vlU", "|ABc"); check("|ABC", "vlu", "|abC"); }
    public static void testSwapAndReselect() { check("a|bcd", "vllohd", "|"); check("|abc", "vl<Esc>gvd", "|c"); checkBeep("|abc", "gv"); }
    public static void testModeSwitch() { eq(Mode.VISUAL_LINE, of("|a").keys("vV").mode()); eq(Mode.NORMAL, of("|a").keys("vv").mode()); eq(Mode.VISUAL, of("|a b").keys("Viw").mode()); eq(Mode.VISUAL_LINE, of("a\n|b\n\nc").keys("vip").mode()); }
    public static void testBlockDelegates() { Fixture f = of("|ab\ncd").keys("<C-v>jld"); eq(Operator.DELETE, f.lastBlockOperator()); eq(Mode.NORMAL, f.mode()); checkRejected("|ab\ncd", "<C-v>jc"); }
    public static void testCharwiseIncludesLineEnd() { check("a\n|\nb", "vd", "a\n|b"); check("|ab\ncd", "v$d", "|cd"); check("|ab\n\ncd", "vjd", "|cd"); check("|ab", "v$d", "|"); eq("ab\n", of("|ab\ncd").keys("v$").selectedText()); }
    public static void testLegacyKeysRejected() { checkRejected("|ab", "vlp"); checkRejected("|ab", "vlI"); checkRejected("|ab", "vlr"); }
    public static void testLastVisualLines() { Fixture f = of("a\n|b\nc\nd").keys("Vj<Esc>"); int[] l = f.engine().lastVisualLines(); eq(1, l[0]); eq(2, l[1]); }
}
