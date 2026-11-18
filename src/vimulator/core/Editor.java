package vimulator.core;

/** The host editor the {@link Engine} drives. Implemented by JEditEditor and, in tests, FakeEditor. */
public interface Editor extends TextModel {
    int caret();
    void setCaret(int offset);

    Mode mode();
    /** Leaving INSERT/REPLACE moves the caret one left (host behaviour). */
    void setMode(Mode m);

    /** Shows a visual selection; anchor and caret are inclusive character offsets. */
    void setSelection(int anchor, int caret, Mode visualMode);

    /** Text in [start, end). */
    String text(int start, int end);
    void replace(int start, int end, String text);
    void yank(String text, boolean linewise);

    /** direction +1 shifts right, -1 shifts left by one indent level. */
    void shiftLines(int firstLine, int lastLine, int direction);
    void reindentLines(int firstLine, int lastLine);
    /** Applies op to the current rectangular selection. */
    void blockOperator(Operator op);
    /** Searches javaRegex, moving the caret; recorded as the last search for n/N. */
    void search(String javaRegex, boolean backward);

    void beginEdit();
    void endEdit();
    void beep();
    String extraWordChars();

    int firstVisibleLine();
    int visibleLineCount();
    void setFirstVisibleLine(int line);
}
