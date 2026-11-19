package nvimulator;

import org.gjt.sp.jedit.Registers;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.Selection;

import nvimulator.core.Editor;
import nvimulator.core.Mode;
import nvimulator.core.Operator;

/** {@link Editor} implementation over a jEdit {@link View}. Resolves the text area on every call. */
public class JEditEditor implements Editor {
    private final View view;

    public JEditEditor(View view) {
        this.view = view;
    }

    private JEditTextArea ta() {
        return view.getTextArea();
    }

    private JEditBuffer buf() {
        return ta().getBuffer();
    }

    // ---- TextModel

    @Override public int length() { return buf().getLength(); }
    @Override public char charAt(int offset) { return buf().getSegment(offset, 1).charAt(0); }
    @Override public int lineCount() { return buf().getLineCount(); }

    @Override public int lineOfOffset(int offset) {
        return buf().getLineOfOffset(Math.min(offset, buf().getLength()));
    }

    @Override public int lineStart(int line) { return buf().getLineStartOffset(line); }

    /** jEdit's line end is one past the newline (length+1 on the last line). */
    @Override public int lineEnd(int line) { return buf().getLineEndOffset(line) - 1; }

    // ---- Editor

    @Override public int caret() { return ta().getCaretPosition(); }
    /**
     * Moves the caret. Electric scrolling (jEdit's scroll margin) only kicks in when the target
     * line is off screen, like nvim's default scrolloff=0, so H/L/C-d land exactly.
     */
    @Override public void setCaret(int offset) {
        JEditTextArea ta = ta();
        int line = buf().getLineOfOffset(offset);
        int first = ta.getFirstPhysicalLine();
        boolean visible = line >= first && line < first + visibleLineCount();
        ta.moveCaretPosition(offset, !visible);
    }

    @Override public Mode mode() {
        return toMode(NvimulatorUtilities.getMode(view));
    }

    @Override public void setMode(Mode m) {
        NvimulatorUtilities.setMode(view, toConstant(m));
    }

    static Mode toMode(int constant) {
        switch (constant) {
            case NvimulatorConstants.VISUAL: return Mode.VISUAL;
            case NvimulatorConstants.VISUAL_LINE: return Mode.VISUAL_LINE;
            case NvimulatorConstants.VISUAL_BLOCK: return Mode.VISUAL_BLOCK;
            case NvimulatorConstants.INSERT: return Mode.INSERT;
            case NvimulatorConstants.REPLACE: return Mode.REPLACE;
            default: return Mode.NORMAL;
        }
    }

    static int toConstant(Mode m) {
        switch (m) {
            case VISUAL: return NvimulatorConstants.VISUAL;
            case VISUAL_LINE: return NvimulatorConstants.VISUAL_LINE;
            case VISUAL_BLOCK: return NvimulatorConstants.VISUAL_BLOCK;
            case INSERT: return NvimulatorConstants.INSERT;
            case REPLACE: return NvimulatorConstants.REPLACE;
            default: return NvimulatorConstants.COMMAND;
        }
    }

    @Override public void setSelection(int anchor, int caret, Mode visualMode) {
        int a = Math.min(anchor, caret), b = Math.max(anchor, caret);
        int len = length();
        Selection s;
        if (visualMode == Mode.VISUAL_LINE) {
            int start = lineStart(lineOfOffset(a));
            int end = Math.min(lineEnd(lineOfOffset(b)) + 1, len);
            s = new Selection.Range(start, end);
        } else if (visualMode == Mode.VISUAL_BLOCK) {
            // corners may be in any order: build the rectangle from its columns
            int lineA = lineOfOffset(anchor), lineC = lineOfOffset(caret);
            int colA = anchor - lineStart(lineA), colC = caret - lineStart(lineC);
            s = new Selection.Rect(buf(), Math.min(lineA, lineC), Math.min(colA, colC),
                    Math.max(lineA, lineC), Math.max(colA, colC) + 1);
        } else {
            s = new Selection.Range(a, Math.min(b + 1, len));
        }
        ta().setSelection(s);
    }

    @Override public String text(int start, int end) { return buf().getText(start, end - start); }

    @Override public void replace(int start, int end, String text) {
        JEditBuffer b = buf();
        if (end > start) b.remove(start, end - start);
        if (!text.isEmpty()) b.insert(start, text);
    }

    @Override public void yank(String text, boolean linewise) {
        Registers.setRegister('y', text);
    }

    @Override public void shiftLines(int firstLine, int lastLine, int direction) {
        int[] lines = new int[lastLine - firstLine + 1];
        for (int i = 0; i < lines.length; i++) lines[i] = firstLine + i;
        if (direction > 0) buf().shiftIndentRight(lines);
        else buf().shiftIndentLeft(lines);
    }

    @Override public void reindentLines(int firstLine, int lastLine) {
        buf().indentLines(firstLine, lastLine);
    }

    @Override public void blockOperator(Operator op) {
        JEditTextArea ta = ta();
        switch (op) {
            case DELETE:
            case CHANGE:
                Registers.setRegister('y', ta.getSelectedText());
                ta.setSelectedText("");
                break;
            case YANK:
                Registers.setRegister('y', ta.getSelectedText());
                break;
            case SHIFT_RIGHT: ta.shiftIndentRight(); break;
            case SHIFT_LEFT: ta.shiftIndentLeft(); break;
            case UPPERCASE: ta.toUpperCase(); break;
            case LOWERCASE: ta.toLowerCase(); break;
            case TOGGLE_CASE:
                for (Selection s : ta.getSelection())
                    ta.setSelectedText(s, nvimulator.core.Transforms.toggleCase(ta.getSelectedText(s)));
                break;
            default:
                beep();
        }
    }

    @Override public void search(String javaRegex, boolean backward) {
        NvimulatorPlugin.getCommandLine(view).search(javaRegex, backward);
    }

    /** Buffers with an open compound edit, so endEdit closes the right one after a buffer switch. */
    private final java.util.ArrayDeque<JEditBuffer> openEdits = new java.util.ArrayDeque<>();

    @Override public void beginEdit() {
        JEditBuffer b = buf();
        b.beginCompoundEdit();
        openEdits.push(b);
    }

    @Override public void endEdit() {
        JEditBuffer b = openEdits.isEmpty() ? buf() : openEdits.pop();
        b.endCompoundEdit();
    }
    @Override public void beep() { NvimulatorUtilities.beep(view); }

    /** jEdit's isEditable covers locked buffers and buffers being saved, not the read-only flag. */
    @Override public boolean editable() { return buf().isEditable() && !buf().isReadOnly(); }

    @Override public String extraWordChars() {
        String s = buf().getStringProperty("noWordSep");
        return s == null ? "" : s;
    }

    @Override public int firstVisibleLine() { return ta().getFirstPhysicalLine(); }
    /** Fully visible lines: jEdit's count includes the partially visible line at the bottom. */
    @Override public int visibleLineCount() { return Math.max(1, ta().getVisibleLines() - 1); }
    @Override public void setFirstVisibleLine(int line) { ta().setFirstPhysicalLine(line); }
}
