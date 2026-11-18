package vimulator.core;

import static java.awt.event.KeyEvent.*;
import static vimulator.core.Fixture.*;

import java.awt.Canvas;
import java.awt.event.KeyEvent;

public class KeyTokensTest {
    static final Canvas SRC = new Canvas();

    static String typed(char ch, int mods) {
        return KeyTokens.of(new KeyEvent(SRC, KEY_TYPED, 0, mods, VK_UNDEFINED, ch));
    }

    static String pressed(int code, int mods) {
        return KeyTokens.of(new KeyEvent(SRC, KEY_PRESSED, 0, mods, code, CHAR_UNDEFINED));
    }

    public static void testTyped() { eq("d", typed('d', 0)); eq("D", typed('D', SHIFT_DOWN_MASK)); eq("$", typed('$', SHIFT_DOWN_MASK)); eq(null, typed('d', CTRL_DOWN_MASK)); eq(null, typed(' ', 0)); }
    public static void testPressed() { eq("<C-d>", pressed(VK_D, CTRL_DOWN_MASK)); eq("<Esc>", pressed(VK_OPEN_BRACKET, CTRL_DOWN_MASK)); eq("<Esc>", pressed(VK_ESCAPE, 0)); eq("<CR>", pressed(VK_ENTER, 0)); eq("<Space>", pressed(VK_SPACE, 0)); eq(null, pressed(VK_PAGE_DOWN, 0)); eq(null, pressed(VK_SHIFT, SHIFT_DOWN_MASK)); eq(null, pressed(VK_D, 0)); }
}
