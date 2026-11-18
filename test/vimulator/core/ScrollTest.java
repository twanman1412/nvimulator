package vimulator.core;

import static vimulator.core.Fixture.*;

public class ScrollTest {
    public static void testCtrlD() { Fixture f = lines(30).keys("<C-d>"); eq(5, f.firstVisibleLine()); eq(5, f.caretLine()); f = lines(30).keys("2<C-d>"); eq(2, f.caretLine()); f.keys("<C-d>"); eq(4, f.caretLine()); eq(4, f.firstVisibleLine()); f = lines(30).viewport(20,10).caretLine(25).keys("<C-d>"); eq(20, f.firstVisibleLine()); eq(29, f.caretLine()); eq(1, lines(30).viewport(20,10).caretLine(29).keys("<C-d>").beeps()); }
    public static void testCtrlU() { Fixture f = lines(30).viewport(10,10).caretLine(15).keys("<C-u>"); eq(5, f.firstVisibleLine()); eq(10, f.caretLine()); eq(1, lines(30).keys("<C-u>").beeps()); }
    public static void testCtrlEY() { Fixture f = lines(30).keys("<C-e>"); eq(1, f.firstVisibleLine()); eq(1, f.caretLine()); f = lines(30).caretLine(5).keys("<C-e>"); eq(5, f.caretLine()); f = lines(30).viewport(5,10).caretLine(14).keys("<C-y>"); eq(4, f.firstVisibleLine()); eq(13, f.caretLine()); }
    public static void testZ() { eq(11, lines(30).caretLine(15).keys("zz").firstVisibleLine()); eq(15, lines(30).caretLine(15).keys("zt").firstVisibleLine()); eq(6, lines(30).caretLine(15).keys("zb").firstVisibleLine()); check("a\n  b|c", "z<CR>", "a\n  |bc"); check("a\n  b|c", "zt", "a\n  b|c"); eq(20, lines(30).keys("21zt").firstVisibleLine()); }
    public static void testHML() { eq(10, lines(30).viewport(10,10).caretLine(15).keys("H").caretLine()); eq(12, lines(30).viewport(10,10).caretLine(15).keys("3H").caretLine()); eq(19, lines(30).viewport(10,10).caretLine(15).keys("L").caretLine()); eq(14, lines(30).viewport(10,10).caretLine(10).keys("M").caretLine()); eq(27, lines(30).viewport(25,10).caretLine(25).keys("M").caretLine()); eq(25, lines(30).viewport(10,10).caretLine(15).keys("dL").editor().lineCount()); }
    public static void testKeepsColumn() { Fixture f = of("ab|c\n" + "abc\n".repeat(20)).keys("<C-d>"); eq(5, f.caretLine()); eq(2, f.editor().caret() - f.editor().lineStart(5)); }
}
