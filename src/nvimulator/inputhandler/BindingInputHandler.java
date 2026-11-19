/*
 * NvimulatorInputHandler.java - Modal InputHandler for Nvimulator Copyright (C) 2000, 2001 mike
 * dillon
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

package nvimulator.inputhandler;

import nvimulator.*;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.*;
import javax.swing.KeyStroke;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditAction;
import org.gjt.sp.jedit.Macros;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.gui.HistoryModel;
import org.gjt.sp.jedit.gui.InputHandler;
import org.gjt.sp.jedit.gui.KeyEventTranslator.Key;
import org.gjt.sp.util.Log;
import nvimulator.core.Engine;
import nvimulator.core.KeyTokens;

public class BindingInputHandler extends InputHandler {

    /** Engine that gets first pick of every key in this mode; null when there is no view. */
    private Engine engine;
    /** Key events fed to the engine since its last DONE, replayed to the legacy bindings on REJECTED. */
    private final List<KeyEvent> engineKeys = new ArrayList<>();

    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    public BindingInputHandler(View view, int mode) {
        super(view);

        this.bindings = new Hashtable();
        this.mode = mode;
        this.setCurrentBindings(bindings);
    }

    public BindingInputHandler(View view, int mode, Hashtable localBindings) {
        super(view);

        this.bindings = localBindings;
        this.mode = mode;
        this.setCurrentBindings(localBindings);
    }

    public BindingInputHandler(View view, BindingInputHandler chain) {
        super(view);

        this.bindings = chain.bindings;
        this.mode = chain.mode;
        this.setCurrentBindings(this.bindings);
    }

    public int getMode() {
        return mode;
    }

	//{{{ invokeAction() method
	/**
	 * Invokes the specified action, repeating and recording it as
	 * necessary.
     * This method is changed with respect to the super method in two aspects
     *  - every iteration checks the global repeatCount, hence its current state is observable
     *  - the user is never asked whether the current action should be repeated
	 * @param action The action
	 */
	@Override
	public void invokeAction(EditAction action)
	{
		JEditBuffer buffer = view.getBuffer();

		/* if(buffer.insideCompoundEdit())
			buffer.endCompoundEdit(); */

		// remember the last executed action
		if(!action.noRememberLast())
		{
			HistoryModel.getModel("action").addItem(action.getName());
			if(lastAction == action)
				lastActionCount++;
			else
			{
				lastAction = action;
				lastActionCount = 1;
			}
		}

		// remember old values, in case action changes them

        if(repeatCount == 1)
            // inside compound edit actions like "undo" don't work anymore
            action.invoke(view);
        else {
            try {
                buffer.beginCompoundEdit();

                for(int i = 0; i < repeatCount; i++)
                    action.invoke(view);
            }
            finally {
                buffer.endCompoundEdit();
            }
        }

		Macros.Recorder recorder = view.getMacroRecorder();

		if(recorder != null && !action.noRecord())
			recorder.record(repeatCount,action.getCode());

		// If repeat was true originally, clear it
		// Otherwise it might have been set by the action, etc
		if(repeatCount != 1)
		{
			// first of all, if this action set a
			// readNextChar, do not clear the repeat
			if(readNextChar != null)
				return;

			repeatCount = 1;
			view.getStatus().setMessage(null);
		}
	} //}}}


    // private members
    // current VI edit mode
    private int mode;
    // User requested a repeated command before
    private boolean requestedRepeat;


    private void resetState() {
        this.setCurrentBindings(bindings);
        this.requestedRepeat = false;
        this.setRepeatCount(1);
    }

    private void commandKeyPressed(KeyEvent evt) {
        int keyCode = evt.getKeyCode();
        int modifiers = evt.getModifiersEx();
        char c = evt.getKeyChar();
        boolean simple = (modifiers & ~KeyEvent.SHIFT_DOWN_MASK) == 0;

        if (!simple || evt.isActionKey() || keyCode == KeyEvent.VK_SPACE
                || keyCode == KeyEvent.VK_BACK_SPACE || keyCode == KeyEvent.VK_DELETE
                || keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_ENTER
                || keyCode == KeyEvent.VK_TAB) {
            readNextChar = null;

            KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers);

            Object o = currentBindings.get(keyStroke);
            if (o instanceof Hashtable) {
                //Log.log(Log.WARNING, this, "Pressed, Result of keystroke: New hashtable");
                currentBindings = (Hashtable) o;
                evt.consume();
                return;
            } else if (o instanceof EditAction) {
                //Log.log(Log.WARNING, this,
                //        "Pressed, Result of keystroke: New Action " + ((EditAction) o).getName());
                invokeAction((EditAction) o);
                evt.consume();
                resetState();
                afterCommand();
                return;
            }

            Toolkit.getDefaultToolkit().beep();
            if (!isModifier(keyCode)) afterCommand();

            if (currentBindings != bindings) {
                // F10 should be passed on, but C+e F10
                // shouldn't
                evt.consume();
            }
            resetState();
        }
    }

    private void commandKeyTyped(KeyEvent evt) {
        int modifiers = evt.getModifiersEx();
        char c = evt.getKeyChar();

        if (readNextChar != null) {
            invokeReadNextChar(c);
            resetState();
            evt.consume();
            return;
        }

        c = Character.toUpperCase(c);

        // ignore
        if (c == '\b' || c == ' ')
            return;

        readNextChar = null;

        KeyStroke keyStroke;
        if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 && c != Character.toLowerCase(c)) {
            // Shift+letter
            //Log.log(Log.WARNING, this, "Shift + " + c);
            keyStroke = KeyStroke.getKeyStroke(c, modifiers);
        } else {
            // Plain letter or Shift+punct
            keyStroke = KeyStroke.getKeyStroke(c);
        }

        Object o = currentBindings.get(keyStroke);

        if (currentBindings == bindings && Character.isDigit(c)
                && (this.requestedRepeat || c != '0')) {
            if (this.requestedRepeat) {
                this.repeatCount *= 10;
                this.repeatCount += c - '0';
            } else
                this.repeatCount = c - '0';
            this.requestedRepeat = true;
            evt.consume();
            return;
        } else if (o instanceof Hashtable) {
            //Log.log(Log.WARNING, this, "Typed, Result of keystroke: New hashtable");
            currentBindings = (Hashtable) o;
            evt.consume();
            return;
        } else if (o instanceof EditAction) {
            //Log.log(Log.WARNING, this,
            //        "Typed, Result of keystroke: New Action " + ((EditAction) o).getLabel());
            invokeAction((EditAction) o);
            evt.consume();
            if (readNextChar == null) {
                resetState();
                afterCommand();
            }
            return;
        }

        Toolkit.getDefaultToolkit().beep();
        resetState();
        afterCommand();
    }

    @Override
    public boolean handleKey(Key key, boolean global) {
        return true;
    }

    @Override
    public void processKeyEvent(java.awt.event.KeyEvent evt, int from, boolean global) {
        if (engine == null || legacyPending()) {
            legacy(evt);
            return;
        }
        String token = KeyTokens.of(evt);
        if (token == null) {
            if (engine.isIdle()) legacy(evt);
            else evt.consume();
            return;
        }
        engineKeys.add(evt);
        switch (engine.feed(token)) {
            case PENDING:
                evt.consume();
                break;
            case DONE:
                engineKeys.clear();
                evt.consume();
                afterCommand();
                break;
            default: // REJECTED: the sequence belongs to the legacy bindings
                List<KeyEvent> replay = new ArrayList<>(engineKeys);
                engineKeys.clear();
                for (KeyEvent e : replay) legacy(e);
                evt.consume();
        }
    }

    private static boolean isModifier(int keyCode) {
        return keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_ALT
                || keyCode == KeyEvent.VK_META || keyCode == KeyEvent.VK_ALT_GRAPH
                || keyCode == KeyEvent.VK_CAPS_LOCK;
    }

    /** Ends an insert-mode C-o window after a complete (or failed) command. */
    private void afterCommand() {
        if (view != null && view.getInputHandler() instanceof NvimulatorInputHandler)
            ((NvimulatorInputHandler) view.getInputHandler()).afterCommand();
    }

    /** True while the legacy table is in the middle of a sequence (prefix key, count or char argument). */
    private boolean legacyPending() {
        return currentBindings != bindings || readNextChar != null || requestedRepeat;
    }

    private void legacy(KeyEvent evt) {
        if (evt.getID() == KeyEvent.KEY_PRESSED) {
            commandKeyPressed(evt);
        } else if (evt.getID() == KeyEvent.KEY_TYPED) {
            commandKeyTyped(evt);
        }
    }
}

