/*
 * VimulatorUtilities.java - Utility class for Vimulator extension functions Copyright (C) 2000,
 * 2002 mike dillon
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 */

package vimulator;

import java.util.*;
import java.awt.Cursor;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import javax.swing.text.Segment;

import vimulator.inputhandler.*;
import vimulator.*;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.util.Log;

import java.lang.Character;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

public class VimulatorUtilities {

    public static boolean checkEmulation(View view) {
        return view.getInputHandler() instanceof VimulatorInputHandler;
    }

    public static int getMode(View view) {
        if (!checkEmulation(view))
            return -1;

        return ((VimulatorInputHandler) view.getInputHandler()).getMode();
    }

    public static void setMode(View view, int mode) {
        setMode(view, mode, true);
    }

    /**
     * @param moveLeft whether leaving insert mode moves the caret one left (false for insert C-o)
     */
    public static void setMode(View view, int mode, boolean moveLeft) {
        if (!checkEmulation(view))
            return;

        JEditTextArea textArea = view.getTextArea();
        
        // when exiting insert mode, let the engine record the inserted text, then move one left
        VimulatorInputHandler handler = (VimulatorInputHandler) view.getInputHandler();
        if ((handler.getMode() & VimulatorConstants.INSERT) != 0
                && (mode & VimulatorConstants.INSERT) == 0 && handler.getEngine() != null)
            handler.getEngine().onInsertExit();
        int caret = textArea.getCaretPosition();
        if (moveLeft && (handler.getMode() & VimulatorConstants.INSERT) != 0
                && caret > textArea.getLineStartOffset(textArea.getCaretLine()))
            textArea.moveCaretPosition(caret - 1);

        Log.log(Log.DEBUG, null, "Setting mode to " + mode);
        ((VimulatorInputHandler) view.getInputHandler()).setMode(mode);

        
        if((mode & VimulatorConstants.INSERT) != 0){
            // restore caret user settings in the insert modes
            textArea.getPainter().setBlockCaretEnabled(jEdit.getBooleanProperty("view.blockCaret"));
            textArea.getPainter().setThickCaretEnabled(jEdit.getBooleanProperty("view.thickCaret"));
            textArea.setCaretBlinkEnabled(jEdit.getBooleanProperty("view.caretBlink"));

        }
        else {
            // otherwise enforce block
            textArea.getPainter().setBlockCaretEnabled(true);
            textArea.getPainter().setThickCaretEnabled(true);
            textArea.setCaretBlinkEnabled(false);
        }

        textArea.setOverwriteEnabled((mode & VimulatorConstants.OVERWRITE) != 0);
        textArea.setRectangularSelectionEnabled((mode & VimulatorConstants.BLOCK) != 0);
        if ((mode & VimulatorConstants.COMMAND) != 0) {
            textArea.selectNone();
        }

        setStatus(view, jEdit.getProperty("vimulator.msg."
                + VimulatorConstants.stringOfMode.get(mode).toLowerCase() + "-mode"));
    }

    /** {@code :wq} / {@code :wq!} / {@code :x}: save, wait for the save to finish, then close. */
    public static void saveCloseBuffer(View view) {
        org.gjt.sp.jedit.Buffer buffer = view.getBuffer();
        buffer.save(view, null, true);
        org.gjt.sp.jedit.io.VFSManager.waitForRequests();
        jEdit.closeBuffer(view, buffer);
    }

    /** {@code :wqa} / {@code :wqa!}: save every buffer, then close them all. */
    public static void saveCloseAll(View view) {
        jEdit.saveAllBuffers(view, false);
        org.gjt.sp.jedit.io.VFSManager.waitForRequests();
        jEdit.closeAllBuffers(view);
    }

    /** {@code :qa!}: close every buffer, discarding changes. */
    public static void closeAllForce(View view) {
        for (org.gjt.sp.jedit.Buffer b : jEdit.getBuffers()) b.setDirty(false);
        jEdit.closeAllBuffers(view);
    }

    /** {@code :} in visual mode: open the ex prompt on the selected lines. */
    public static void visualExCommand(View view) {
        setCommandMode(view);
        VimulatorPlugin.getCommandLine(view).beginExCommand("'<,'>");
    }

    /** Insert-mode C-w: delete the word before the caret. */
    public static void insertDeleteWord(View view) {
        if (!new JEditEditor(view).editable()) {
            beep(view);
            return;
        }
        JEditEditor ed = new JEditEditor(view);
        int caret = ed.caret();
        int start = vimulator.core.InsertEdits.deleteWordStart(ed,
                new vimulator.core.CharClass(ed.extraWordChars()), caret);
        if (start < caret) view.getBuffer().remove(start, caret - start);
    }

    /** Insert-mode C-u: delete back to the indent, the line start, or the previous line. */
    public static void insertDeleteLine(View view) {
        if (!new JEditEditor(view).editable()) {
            beep(view);
            return;
        }
        JEditEditor ed = new JEditEditor(view);
        int caret = ed.caret();
        int start = vimulator.core.InsertEdits.deleteLineStart(ed, caret);
        if (start < caret) view.getBuffer().remove(start, caret - start);
    }

    /** Insert-mode C-o: run one normal-mode command, then return to insert mode. */
    public static void insertOneCommand(View view) {
        if (!checkEmulation(view)) return;
        ((VimulatorInputHandler) view.getInputHandler()).setReturnToInsert(true);
        setMode(view, VimulatorConstants.COMMAND, false);
    }

    public static void setInsertMode(View view) {
        setMode(view, VimulatorConstants.INSERT);
    }

    public static void setReplaceMode(View view) {
        setMode(view, VimulatorConstants.REPLACE);
    }

    public static void setCommandMode(View view) {
        setMode(view, VimulatorConstants.COMMAND);
    }

    public static void beep(View view) {
        view.getToolkit().beep();
    }

    public static void setStatus(View view, String status) {
        VimulatorPlugin.getCommandLine(view).setStatus(status);
    }

    public static int getLastAllowedOffset(View view, JEditTextArea textArea) {
        return getLastAllowedOffset(view, textArea, textArea.getCaretLine());
    }

    public static int getLastAllowedOffset(View view, JEditTextArea textArea, int line) {
        if (line < 0 || line >= textArea.getLineCount()) {
            // view.getToolkit().beep();
            return -1;
        }

        int lastAllowed = textArea.getLineEndOffset(line) - 1;
        if (checkEmulation(view) && ((VimulatorInputHandler) view.getInputHandler())
                .getMode() == VimulatorConstants.COMMAND)
            lastAllowed -= 1;

        lastAllowed = Math.max(lastAllowed, textArea.getLineStartOffset(line));

        return lastAllowed;
    }

    public static KeyStroke parseKeyStroke(String keyStroke) {
        if (keyStroke == null)
            return null;
        int modifiers = 0;
        int index = keyStroke.indexOf('+');
        if (index == 0)
            index = -1;
        for (int i = 0; i < index; i++) {
            switch (Character.toUpperCase(keyStroke.charAt(i))) {
                case 'A':
                    modifiers |= KeyEvent.ALT_DOWN_MASK;
                    break;
                case 'C':
                    modifiers |= KeyEvent.CTRL_DOWN_MASK;
                    break;
                case 'M':
                    modifiers |= KeyEvent.META_DOWN_MASK;
                    break;
                case 'S':
                    modifiers |= KeyEvent.SHIFT_DOWN_MASK;
                    break;
            }
        }
        String key = keyStroke.substring(index + 1);
        if (key.length() == 1) {
            char ch = Character.toUpperCase(key.charAt(0));
            if (modifiers == 0)
                return KeyStroke.getKeyStroke(ch);
            else
                return KeyStroke.getKeyStroke(ch, modifiers);
        } else if (key.length() == 0) {
            Log.log(Log.ERROR, VimulatorUtilities.class, "Invalid key stroke: " + keyStroke);
            return null;
        } else {
            int ch;

            try {
                ch = KeyEvent.class.getField("VK_".concat(key)).getInt(null);
            } catch (Exception e) {
                Log.log(Log.ERROR, VimulatorUtilities.class, "Invalid key stroke: " + keyStroke);
                return null;
            }

            return KeyStroke.getKeyStroke(ch, modifiers);
        }
    }

    public static void yank(String s) {
        Registers.setRegister('y', s);
    }

    public static String unYank() {
        return Registers.getRegister('y').toString();
    }

    public static void paste(JEditTextArea textArea, JEditBuffer buffer, boolean after) {
        String s = unYank();
        int caretPos;
        if (s.endsWith("\n") || s.endsWith("\r")) {
            int caretLine = textArea.getCaretLine();
            if (after)
                caretPos = textArea.getLineEndOffset(caretLine);
            else
                caretPos = textArea.getLineStartOffset(caretLine);
        } else
            caretPos = textArea.getCaretPosition() + (after ? 1 : 0);
        buffer.insert(caretPos, s);
    }

    public static void yankSelection(JEditTextArea textArea) {
        yank(textArea.getSelectedText());
    }

    public static void replaceSelection(JEditTextArea textArea, String s) {
        yankSelection(textArea);
        textArea.setSelectedText(s);
    }

    public static void pasteSelection(JEditTextArea textArea) {
        replaceSelection(textArea, unYank());
    }

    public static void replaceSelection(JEditTextArea textArea, char c) {
        String text = textArea.getSelectedText();
        String newtext = text.replaceAll("[^\n\r]", Character.toString(c));
        replaceSelection(textArea, newtext);
    }

    public static int MULTILINE_INSERT = 1;
    public static int MULTILINE_CHANGE = 2;
    public static int MULTILINE_APPEND = 3;

    public static void multilineInsert(View view, JEditTextArea textArea) {
        multilineEdit(view, textArea, MULTILINE_INSERT);
    }

    public static void multilineChange(View view, JEditTextArea textArea) {
        multilineEdit(view, textArea, MULTILINE_CHANGE);
    }

    public static void multilineAppend(View view, JEditTextArea textArea) {
        multilineEdit(view, textArea, MULTILINE_APPEND);
    }

    public static void multilineEdit(View view, JEditTextArea textArea, int mode) {
        // Some black magic to enable multi-caret editing (insertion and replacement)
        // Idea courtesy to Skeeve https://sourceforge.net/p/jedit/feature-requests/499/
        JEditBuffer buffer = textArea.getBuffer();
        List<Selection> news = new ArrayList<Selection>(textArea.getSelectedLines().length);
        int selectionStart = buffer.getLength();
        Selection s = textArea.getSelection()[0];
        selectionStart = Math.min(selectionStart, s.getStart());

        if(s instanceof Selection.Rect && s.getStart() != s.getEnd()){
            // Special case: for rectangles with non-zero line difference,
            // a selection of zero width is allowed
            Selection.Rect r = (Selection.Rect) s;
            news.add(new Selection.Rect(
                buffer,
                r.getStartLine(),
                mode == MULTILINE_APPEND ? r.getEndColumn(buffer) : r.getStartColumn(buffer),
                r.getEndLine(),
                mode == MULTILINE_INSERT ? r.getStartColumn(buffer) : r.getEndColumn(buffer)
            ));
        }
        else{
            for (int j : textArea.getSelectedLines()) {
                int start = s.getStart(buffer, j);
                int end = s.getEnd(buffer, j);
                // TODO insert dummy value at selection beginnings
                // for correct multi-caret insertion
                news.add(new Selection.Rect(
                    mode == MULTILINE_APPEND ? end - 1 : start,
                    mode == MULTILINE_INSERT ? start + 1: end
                ));
        }
        }
        textArea.setSelection(news.toArray(new Selection.Rect[0]));
        textArea.moveCaretPosition(selectionStart);
        setInsertMode(view);
    }

    // private members
    private VimulatorUtilities() {
    }

}
