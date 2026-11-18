package vimulator.core;

import static vimulator.core.Fixture.*;

public class WordMotionTest {
    public static void testW() { check("|foo.bar baz", "w", "foo|.bar baz"); check("|foo.bar baz", "3w", "foo.bar |baz"); check("|foo\n\nbar", "w", "foo\n|\nbar"); check("|foo\n\nbar", "2w", "foo\n\n|bar"); check("foo |bar", "w", "foo ba|r"); checkBeep("foo ba|r", "w"); }
    public static void testBigW() { check("|foo.bar baz", "W", "foo.bar |baz"); check("a foo.ba|r b", "B", "a |foo.bar b"); check("|a foo.bar b", "E", "a foo.ba|r b"); }
    public static void testB() { check("foo ba|r", "b", "foo |bar"); check("foo |bar", "b", "|foo bar"); check("foo\n\n|bar", "b", "foo\n|\nbar"); checkBeep("|foo", "b"); }
    public static void testE() { check("|foo bar", "e", "fo|o bar"); check("fo|o bar", "e", "foo ba|r"); check("fo|o\n\n  bar", "e", "foo\n\n  ba|r"); }
    public static void testGe() { check("foo ba|r", "ge", "fo|o bar"); check("foo\n\n|bar", "ge", "foo\n|\nbar"); check("a.b c|d", "gE", "a.|b cd"); checkBeep("|foo", "ge"); }
    public static void testParagraph() { check("|a\nb\n\nc", "}", "a\nb\n|\nc"); check("a\n\nb\n|c", "{", "a\n|\nb\nc"); check("|a\nb", "}", "a\n|b"); check("a\n|b", "{", "|a\nb"); }
}
