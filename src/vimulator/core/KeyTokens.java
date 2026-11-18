package vimulator.core;

import java.awt.event.KeyEvent;

/** Converts AWT key events into engine tokens ("d", "$", "<C-d>", "<Esc>", ...). */
public final class KeyTokens {
    private KeyTokens() {}

    /** @return the token for this event, or null when the event carries none */
    public static String of(KeyEvent e) {
        int mods = e.getModifiersEx();
        boolean ctrl = (mods & KeyEvent.CTRL_DOWN_MASK) != 0;
        boolean alt = (mods & KeyEvent.ALT_DOWN_MASK) != 0;
        boolean meta = (mods & KeyEvent.META_DOWN_MASK) != 0;

        if (e.getID() == KeyEvent.KEY_TYPED) {
            // Ctrl+Alt together is AltGr on some platforms and produces normal characters.
            if (meta || (ctrl != alt)) return null;
            char ch = e.getKeyChar();
            if ((ch >= 0x21 && ch <= 0x7e) || ch >= 0xa0 && ch != KeyEvent.CHAR_UNDEFINED)
                return String.valueOf(ch);
            return null;
        }

        if (e.getID() != KeyEvent.KEY_PRESSED || alt || meta) return null;
        int code = e.getKeyCode();
        if (ctrl) {
            if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z)
                return "<C-" + (char) ('a' + (code - KeyEvent.VK_A)) + ">";
            if (code == KeyEvent.VK_OPEN_BRACKET) return "<Esc>";
            return null;
        }
        switch (code) {
            case KeyEvent.VK_ESCAPE: return "<Esc>";
            case KeyEvent.VK_ENTER: return "<CR>";
            case KeyEvent.VK_BACK_SPACE: return "<BS>";
            case KeyEvent.VK_SPACE: return "<Space>";
            case KeyEvent.VK_TAB: return "<Tab>";
            case KeyEvent.VK_DELETE: return "<Del>";
            case KeyEvent.VK_LEFT: return "<Left>";
            case KeyEvent.VK_RIGHT: return "<Right>";
            case KeyEvent.VK_UP: return "<Up>";
            case KeyEvent.VK_DOWN: return "<Down>";
            case KeyEvent.VK_HOME: return "<Home>";
            case KeyEvent.VK_END: return "<End>";
            default: return null;
        }
    }
}
