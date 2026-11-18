package vimulator.smoke;

import java.awt.event.KeyEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.Registers;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.textarea.JEditTextArea;

import vimulator.VimulatorUtilities;

/**
 * End-to-end smoke test inside a real jEdit: feeds synthetic key events through the view's
 * input handler and compares buffer text + caret ("|" marks the caret, as in the unit tests).
 * Started by scripts/smoke.sh via jEdit's -run option; writes results to $VIMULATOR_SMOKE_OUT.
 */
public class Smoke {
    static final String LONG = "|" + longText();

    static String longText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append(i == 0 ? "" : "\n").append("line ").append(i);
        return sb.toString();
    }

    /** {before, keys, after} */
    static final String[][] CASES = {
        // Task 3: engine motions and legacy replay
        {"|a\nb\nc\nd\ne\nf", "5j", "a\nb\nc\nd\ne\n|f"},
        {"ab|c", "0", "|abc"},
        {"a|bc", "$", "ab|c"},
        {"|a\nb\nc", "3G", "a\nb\n|c"},
        {"a\nb\n|c", "gg", "|a\nb\nc"},
        {"|abc", "ix<Esc>", "|xabc"},
        // Task 4: word / paragraph motions
        {"|foo.bar baz", "3w", "foo.bar |baz"},
        {"foo ba|r", "b", "foo |bar"},
        {"|foo bar", "ee", "foo ba|r"},
        {"|a\nb\n\nc", "}", "a\nb\n|\nc"},
        // Task 5: find / % / marks
        {"|a,b,c,d", "f,;", "a,b|,c,d"},
        {"|(a [b] c)", "%", "(a [b] c|)"},
        {"ab|c\nd", "majmb`a", "ab|c\nd"},
        // Task 6: operators
        {"foo |bar baz", "dw", "foo |baz"},
        {"foo |bar baz", "cwxy<Esc>", "foo x|y baz"},
        {"|a\nb\nc\nd", "2dd", "|c\nd"},
        {"|a b c d e f g", "2d3w", "|g"},
        {"foo(|bar)", "dt)", "foo(|)"},
        {"ab|c", "x", "a|b"},
        {"a|bc def", "D", "|a"},
        {"|a\nb\nc\nd", "x3ddu", "\nb\nc\n|d"}, // one undo unit; caret placement is jEdit undo behaviour
        {"a\n|b\nc", "dj", "|a"},
        // Task 7: text objects
        {"x = \"he|llo\" y", "ca\"z<Esc>", "x = |zy"},
        {"a[1, |2] b", "da[", "a| b"},
        {"foo b|ar baz", "ciwqq<Esc>", "foo q|q baz"},
        {"<a><b>|x</b></a>", "dit", "<a><b>|</b></a>"},
        {"f(a, |b)", "di(", "f(|)"},
        // Task 8: case / shift / join / increment / replace
        {"a |bc d", "gUiw", "a |BC d"},
        {"|ab", "~", "A|b"},
        {"|a\n  b", "J", "a| b"},
        {"x |9 y", "<C-a>", "x 1|0 y"},
        {"|abc", "2rx", "x|xc"},
        // Task 9: visual modes
        {"|foo bar", "vlld", "| bar"},
        {"foo b|ar baz", "viwd", "foo | baz"},
        {"|a\nb\nc", "Vjd", "|c"},
        {"|abc", "vl~", "|ABc"},
        {"a|bcd", "vllohd", "|"},
        {"|abc", "vl<Esc>gvd", "|c"},
        {"|ab\ncd\nef", "<C-v>jld", "\n|\nef"}, // block delete; caret placed by jEdit
        {"|ab\ncd", "<C-v>jIx<Esc>", "|xab\nxcd"}, // legacy block insert still works
        {"|ab\ncd", "ylvlp", "|a\ncd"}, // legacy visual paste still works
        // Task 10: scrolling (window-height independent round trips)
        {LONG, "<C-d><C-u>", LONG},
        {LONG, "<C-d><C-d><C-u><C-u>", LONG},
        {LONG, "LH", LONG},
        {LONG, "zzzt5j<C-e><C-y>5k", LONG},
        // Task 11: insert commands and dot repeat
        {"|foo foo", "cwbar<Esc>w.", "bar ba|r"},
        {"|a\nb", "Ax<Esc>j.", "ax\nb|x"},
        {"|a", "3ix<Esc>", "xx|xa"},
        {"|a\nb", "ox<Esc>.", "a\nx\n|x\nb"},
        {"|a b c d", "dw.", "|c d"},
        {"|foo foo", "cwbar<Esc>w.u", "bar fo|o"}, // . is one undo unit; caret placed by jEdit undo
        {"a|bc", "Cxy<Esc>", "ax|y"},
        {"foo |bar", "cwxy<Esc>", "foo x|y"},
        {"  |ab\nc", "ccx<Esc>", "  |x\nc"},
        // Task 12: * # and n/N direction
        {"|foo bar foo baz foo", "*", "foo bar |foo baz foo"},
        {"|foo bar foo baz foo", "*n", "foo bar foo baz |foo"},
        {"foo bar foo baz |foo", "#", "foo bar |foo baz foo"},
        {"foo bar foo baz |foo", "#n", "|foo bar foo baz foo"},
        {"foo bar foo baz |foo", "#N", "foo bar foo baz |foo"},
        {"|foo foobar foo", "g*", "foo |foobar foo"},
        // Task 13: insert-mode C-w / C-u / C-o
        {"|", "ifoo bar<C-w><Esc>", "foo| "},
        {"|", "i  foo<C-u>x<Esc>", "  |x"},
        {"|foo", "A<C-o>0x<Esc>", "|xfoo"},
        {"|abc", "i<C-o>$x<Esc>", "abc|x"},
        // legacy replay (p/P are still legacy; legacy paste does not move the caret)
        {"|abc\nx", "yyp", "|abc\nabc\nx"},
        {"|abc\nx", "Y3p", "|abc\nabc\nabc\nabc\nx"},
        {"|abc\nx", "yy3p", "|abc\nabc\nabc\nabc\nx"},
        {"|abc", "<C-w>v<C-w>o0", "|abc"},
    };

    public static void start() {
        Timer t = new Timer(500, null);
        t.addActionListener(e -> {
            View view = jEdit.getActiveView();
            if (view == null || !VimulatorUtilities.checkEmulation(view)) return;
            t.stop();
            String out = System.getenv("VIMULATOR_SMOKE_OUT");
            StringBuilder report = new StringBuilder();
            int failed = 0;
            for (String[] c : CASES) {
                String result;
                try {
                    result = runCase(view, c[0], c[1]);
                } catch (Throwable ex) {
                    result = "threw " + ex;
                }
                boolean ok = result.equals(c[2]);
                if (!ok) failed++;
                report.append(ok ? "PASS " : "FAIL ").append(show(c[0])).append(" ").append(show(c[1]))
                      .append(ok ? "" : " expected <" + show(c[2]) + "> got <" + show(result) + ">").append('\n');
            }
            report.append(CASES.length - failed).append(" passed, ").append(failed).append(" failed\n");
            try (FileWriter w = new FileWriter(out)) {
                w.write(report.toString());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            view.getBuffer().setDirty(false);
            jEdit.exit(view, false);
        });
        t.start();
    }

    static String runCase(View view, String before, String keys) {
        Buffer buffer = view.getBuffer();
        JEditTextArea ta = view.getTextArea();
        VimulatorUtilities.setCommandMode(view);
        int caret = before.indexOf('|');
        String text = before.substring(0, caret) + before.substring(caret + 1);
        buffer.remove(0, buffer.getLength());
        buffer.insert(0, text);
        ta.setCaretPosition(caret);
        for (String tok : tokenize(keys)) send(view, ta, tok);
        int c = ta.getCaretPosition();
        String now = buffer.getText(0, buffer.getLength());
        return now.substring(0, c) + "|" + now.substring(c);
    }

    static void send(View view, JEditTextArea ta, String tok) {
        long when = System.currentTimeMillis();
        if (tok.length() == 1) {
            char ch = tok.charAt(0);
            int mods = (Character.isUpperCase(ch) || "~!@#$%^&*()_+{}|:\"<>?".indexOf(ch) >= 0)
                    ? KeyEvent.SHIFT_DOWN_MASK : 0;
            dispatch(view, new KeyEvent(ta, KeyEvent.KEY_PRESSED, when, mods,
                    KeyEvent.getExtendedKeyCodeForChar(ch), ch));
            dispatch(view, new KeyEvent(ta, KeyEvent.KEY_TYPED, when, mods, KeyEvent.VK_UNDEFINED, ch));
            dispatch(view, new KeyEvent(ta, KeyEvent.KEY_RELEASED, when, mods,
                    KeyEvent.getExtendedKeyCodeForChar(ch), ch));
            return;
        }
        String name = tok.substring(1, tok.length() - 1);
        int mods = 0, code;
        if (name.startsWith("C-")) {
            mods = KeyEvent.CTRL_DOWN_MASK;
            code = KeyEvent.getExtendedKeyCodeForChar(name.charAt(2));
        } else {
            switch (name) {
                case "Esc": code = KeyEvent.VK_ESCAPE; break;
                case "CR": code = KeyEvent.VK_ENTER; break;
                case "BS": code = KeyEvent.VK_BACK_SPACE; break;
                case "Space": code = KeyEvent.VK_SPACE; break;
                case "Tab": code = KeyEvent.VK_TAB; break;
                default: throw new IllegalArgumentException(tok);
            }
        }
        dispatch(view, new KeyEvent(ta, KeyEvent.KEY_PRESSED, when, mods, code, KeyEvent.CHAR_UNDEFINED));
        dispatch(view, new KeyEvent(ta, KeyEvent.KEY_RELEASED, when, mods, code, KeyEvent.CHAR_UNDEFINED));
    }

    static void dispatch(View view, KeyEvent e) {
        view.getInputHandler().processKeyEvent(e, View.TEXT_AREA, false);
    }

    static List<String> tokenize(String keys) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < keys.length(); i++) {
            char c = keys.charAt(i);
            int close = c == '<' ? keys.indexOf('>', i) : -1;
            if (close > i + 1) {
                String name = keys.substring(i, close + 1);
                out.add(name.equals("<lt>") ? "<" : name);
                i = close;
            } else {
                out.add(String.valueOf(c));
            }
        }
        return out;
    }

    static String show(String s) {
        return s.replace("\n", "\\n");
    }
}
