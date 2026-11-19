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
import java.util.*;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import org.gjt.sp.jedit.EditAction;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultInputHandler;
import org.gjt.sp.jedit.gui.InputHandler;
import org.gjt.sp.jedit.gui.KeyEventTranslator.Key;
import org.gjt.sp.jedit.textarea.Selection;
import org.gjt.sp.util.Log;

public class NvimulatorInputHandler extends InputHandler {

    // private members
    // current VI edit mode
    private int mode;

    private InputHandler currentHandler;
    private nvimulator.core.Engine engine;
    private Map<Integer, Hashtable> bindingOfMode;
    private Map<Integer, InputHandler> handlerOfMode;

    public NvimulatorInputHandler(View view) {
        super(view);

        bindingOfMode = new Hashtable<>();
        for(int m : NvimulatorConstants.editorModes)
            bindingOfMode.put(m, new Hashtable<>());
        // manually bind replace to insert
        bindingOfMode.put(NvimulatorConstants.REPLACE, bindingOfMode.get(NvimulatorConstants.INSERT));

        initHandlers();
        setMode(NvimulatorConstants.COMMAND);
    }

    public NvimulatorInputHandler(View view, NvimulatorInputHandler chain) {
        super(view);
        this.bindings = new Hashtable<>();
        bindingOfMode = chain.bindingOfMode;
        if (view != null)
            engine = new nvimulator.core.Engine(new JEditEditor(view));

        initHandlers();
        setMode(chain.getMode());
    }

    private void initHandlers(){
        handlerOfMode = new Hashtable<>();
        for(int m : NvimulatorConstants.editorModes) {
            BindingInputHandler h = new BindingInputHandler(view, m, bindingOfMode.get(m));
            h.setEngine(engine);
            handlerOfMode.put(m, h);
        }
        // manually bind insert and replace to the special handler
        handlerOfMode.put(
            NvimulatorConstants.INSERT,
            new InsertInputHandler(view, bindingOfMode.get(NvimulatorConstants.INSERT))
        );
        handlerOfMode.put(NvimulatorConstants.REPLACE, handlerOfMode.get(NvimulatorConstants.INSERT));
    }

    public int getMode() {
        return mode;
    }

    /** Set by insert-mode C-o: return to insert mode after the next complete command. */
    private boolean returnToInsert;

    public void setReturnToInsert(boolean b) {
        returnToInsert = b;
    }

    /** Called after every complete normal-mode command (engine or legacy). */
    public void afterCommand() {
        if (!returnToInsert) return;
        returnToInsert = false;
        if (mode != NvimulatorConstants.COMMAND) return; // the command chose its own mode
        boolean toLineEnd = engine != null && engine.wantsLineEnd();
        NvimulatorUtilities.setInsertMode(view);
        if (toLineEnd) {
            org.gjt.sp.jedit.textarea.JEditTextArea ta = view.getTextArea();
            ta.moveCaretPosition(ta.getLineEndOffset(ta.getCaretLine()) - 1);
        }
    }

    /** The per-view command engine, or null for the root handler. */
    public nvimulator.core.Engine getEngine() {
        return engine;
    }

    public void setMode(int mode) {
        if (this.view == null) return;

        this.currentHandler = handlerOfMode.getOrDefault(mode, this.currentHandler);

        this.mode = mode;
    }

    public void addKeyBinding(String binding, EditAction action) {
        addKeyBinding(binding, action, this.bindings);
    }

    public void addKeyBinding(String binding, EditAction action, int mode) {
        addKeyBinding(binding, action, this.bindingOfMode.get(mode));
    }

    public void removeKeyBinding(String binding) {
        throw new InternalError("Not yet implemented");
    }

    public void removeAllKeyBindings() {
        bindings.clear();
    }

    public void removeAllKeyBindings(int mode) {
        this.bindingOfMode.get(mode).clear();
    }

    private void addKeyBinding(String binding, EditAction action, Hashtable current) {
        // current is a hashtable that recursively refers to further hashtables until
        // as long as there are more string tokens
        StringTokenizer st = new StringTokenizer(binding);
        while (st.hasMoreTokens()) {
            KeyStroke keyStroke = NvimulatorUtilities.parseKeyStroke(st.nextToken());
            if (keyStroke == null)
                return;

            if (st.hasMoreTokens()) {
                Object o = current.getOrDefault(keyStroke, null);
                if (!(o instanceof Hashtable)) {
                    o = new Hashtable();
                    current.put(keyStroke, o);
                }
                current = (Hashtable) o;
            } else {
                current.put(keyStroke, action);
            }
        }
    }

    @Override
    public int getRepeatCount() {
        return this.currentHandler.getRepeatCount();
    }

    @Override
    public void setRepeatCount(int i) {
        this.currentHandler.setRepeatCount(i);
    }


    @Override
    public boolean handleKey(Key key, boolean global) {
        return true;
    }

    @Override
    public void processKeyEvent(java.awt.event.KeyEvent evt, int from, boolean global) {
        //Log.log(Log.WARNING, this, "KeyCode: " + evt.getKeyCode());
        //Log.log(Log.WARNING, this, "KeyChar: " + evt.getKeyChar());
        //Log.log(Log.WARNING, this, "Modifier: " + evt.getModifiersEx());
        //Log.log(Log.WARNING, this, "ID: " + evt.getID());
        // special case: operations that modify exactly the next operation
        if(this.readNextChar != null){
            if(evt.getID() == KeyEvent.KEY_TYPED)
                this.invokeReadNextChar(evt.getKeyChar());
            return;
        }
        this.currentHandler.processKeyEvent(evt, from, global);
    }

    @Override
    public int getLastActionCount() {
        return this.currentHandler.getLastActionCount();
    }

    @Override
    public EditAction getLastAction() {
        return this.currentHandler.getLastAction();
    }
}
