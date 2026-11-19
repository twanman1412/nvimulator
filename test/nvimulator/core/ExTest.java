package nvimulator.core;

import static nvimulator.core.Fixture.*;

import java.util.List;

import nvimulator.core.ex.ExRange;
import nvimulator.core.ex.Substitute;
import nvimulator.core.ex.SubstituteCommand;
import nvimulator.core.ex.VimRegex;

public class ExTest {
    public static void testVimRegex() { eq("foo(bar)", VimRegex.toJava("foo\\(bar\\)")); eq("a\\+b", VimRegex.toJava("a+b")); eq("\\bx\\b", VimRegex.toJava("\\<x\\>")); eq("a|b", VimRegex.toJava("a\\|b")); eq("(a|b)+", VimRegex.toJava("\\v(a|b)+")); eq("x{2,3}", VimRegex.toJava("x\\{2,3}")); eq("[(]a.*", VimRegex.toJava("[(]a.*")); }
    public static void testReplacement() { eq("[$0]", VimRegex.replacementToJava("[&]")); eq("$1-", VimRegex.replacementToJava("\\1-")); eq("a\nb", VimRegex.replacementToJava("a\\rb")); eq("\\$", VimRegex.replacementToJava("$")); eq("&", VimRegex.replacementToJava("\\&")); }
    public static void testRange() { eq(new ExRange(0, 9, "s/a/b/"), ExRange.parse("%s/a/b/", 3, 9, null)); eq(new ExRange(3, 3, "s/a/b/"), ExRange.parse("s/a/b/", 3, 9, null)); eq(new ExRange(1, 4, "d"), ExRange.parse("2,5d", 3, 9, null)); eq(new ExRange(3, 5, "s/x/y"), ExRange.parse(".,+2s/x/y", 3, 9, null)); eq(new ExRange(4, 6, "s/a/b"), ExRange.parse("'<,'>s/a/b", 0, 9, new int[]{4, 6})); eq(new ExRange(9, 9, ""), ExRange.parse("$", 0, 9, null)); eq(new ExRange(1, 4, ""), ExRange.parse("5,2", 0, 9, null)); eq(new ExRange(3, 3, "w"), ExRange.parse("w", 3, 9, null)); }
    public static void testRangeErrors() { boolean threw = false; try { ExRange.parse("'<s/a/b", 0, 9, null); } catch (IllegalArgumentException e) { threw = true; } eq(true, threw); }
    public static void testParseSub() { SubstituteCommand c = SubstituteCommand.parse("s#a/b#c#gi"); eq("a/b", c.pattern()); eq("c", c.replacement()); eq(true, c.global()); eq(true, c.ignoreCase()); eq("", SubstituteCommand.parse("s//x").pattern()); eq("x", SubstituteCommand.parse("substitute/a/x").replacement()); eq(null, SubstituteCommand.parse("set")); eq("a/b", SubstituteCommand.parse("s/a\\/b/c/").pattern()); }
    public static void testUnsupportedSubstituteFlagRejected() { boolean threw = false; try { SubstituteCommand.parse("s/a/b/gc"); } catch (IllegalArgumentException e) { threw = true; } eq(true, threw); }
    public static void testSubstitute() { var r = Substitute.apply(List.of("aa", "ba", "c"), 0, 2, "a", "x", false, false); eq(List.of("xa", "bx", "c"), r.lines()); eq(2, r.substitutions()); eq(1, r.lastChangedLine()); eq(List.of("xx"), Substitute.apply(List.of("aA"), 0, 0, "a", "x", true, true).lines()); eq(0, Substitute.apply(List.of("b"), 0, 0, "a", "x", true, false).substitutions()); eq(2, Substitute.apply(List.of("aa"), 0, 0, "a", "x", true, false).substitutions()); }
}
