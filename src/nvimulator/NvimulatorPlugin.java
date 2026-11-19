/*
 * NvimulatorPlugin.java - Nvimulator plugin Copyright (C) 2000, 2001, 2002 mike dillon
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

package nvimulator;

import nvimulator.inputhandler.*;
import java.beans.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.event.*;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.msg.*;
import org.gjt.sp.jedit.textarea.*;

import org.gjt.sp.util.Log;

public class NvimulatorPlugin extends EBPlugin {

    @Override
    public void start() {
        Log.log(Log.DEBUG, this, "Started Plugin");
        initKeyBindings();
    }

    // public JMenuItem createMenuItems()
    // {
    // return new JMenuItem[]{GUIUtilities.loadMenuItem("toggle-vi-mode")};
    // }

    public void createOptionPanes(OptionsDialog optionsDialog) {
        optionsDialog.addOptionPane(new NvimulatorOptionPane());
    }

    public void handleMessage(EBMessage msg) {
        if (msg instanceof ViewUpdate) {
            ViewUpdate vmsg = (ViewUpdate) msg;
            View view = vmsg.getView();

            if (vmsg.getWhat() == ViewUpdate.CREATED) {
                commandLines.put(view, new NvimulatorCommandLine(view));
                handlers.put(view, new NvimulatorInputHandler(view, rootHandler));

                if (jEdit.getBooleanProperty("nvimulator.enabled", true)) {
                    toggleEmulation(view);
                }
            } else if (vmsg.getWhat() == ViewUpdate.CLOSED) {
                commandLines.remove(view);
                handlers.remove(view);
            }
        } else if (msg instanceof BufferUpdate) {
            BufferUpdate bmsg = (BufferUpdate) msg;
            Buffer buffer = bmsg.getBuffer();

            if (bmsg.getWhat() == BufferUpdate.LOADED) {
                int lineCount = buffer.getLineCount();
                int charCount = buffer.getLength();
                String[] args = {buffer.getName(), Integer.toString(lineCount),
                        Integer.toString(charCount)};

                updateBufferStatus(buffer, jEdit.getProperty("nvimulator.msg.simple-status", args),
                        null);
            }
        } else if (msg instanceof EditPaneUpdate) {
            EditPaneUpdate emsg = (EditPaneUpdate) msg;
            EditPane editPane = emsg.getEditPane();

            if (emsg.getWhat() == EditPaneUpdate.BUFFER_CHANGED) {
                Buffer buffer = editPane.getBuffer();

                List<View> forViews = Arrays.asList(new View[] {editPane.getView()});

                if (buffer.isNewFile()) {
                    updateBufferStatus(buffer, null, forViews);
                    return;
                }

                int lineCount = buffer.getLineCount();
                int charCount = buffer.getLength();
                String[] args = {buffer.getName(), Integer.toString(lineCount),
                        Integer.toString(charCount)};

                updateBufferStatus(buffer, jEdit.getProperty("nvimulator.msg.simple-status", args),
                        forViews);
            } else if (emsg.getWhat() == EditPaneUpdate.CREATED) {
                JEditTextArea textArea = editPane.getTextArea();
                NvimulatorListener vcl = new NvimulatorListener(editPane.getView(), textArea);
                textArea.addCaretListener(vcl);
                textArea.addKeyListener(vcl);
            }
        }
    }

    public static String getVersionMessage() {
        return "Nvimulator " + jEdit.getProperty("plugin.nvimulator.NvimulatorPlugin.version");
    }

    public static void toggleEmulation(View view) {
        InputHandler newHandler = (InputHandler) handlers.get(view);

        // Check if we are switching to NvimulatorInputHandler
        // or back to the default
        if (newHandler instanceof NvimulatorInputHandler) {
            // Install command line
            view.addToolBar(View.BOTTOM_GROUP, View.ABOVE_SYSTEM_BAR_LAYER, getCommandLine(view));
        } else {
            // Remove command line
            view.removeToolBar(getCommandLine(view));
        }

        // Store original handler
        handlers.put(view, view.getInputHandler());

        // Install new handler
        view.setInputHandler(newHandler);
        NvimulatorUtilities.setMode(view, NvimulatorConstants.COMMAND);
    }

    public static boolean emulationActive(View view) {
        return !(handlers.get(view) instanceof NvimulatorInputHandler);
    }

    public static void enterExCommand(View view) {
        getCommandLine(view).beginExCommand();
    }

    public static void leaveExCommand(View view) {
        getCommandLine(view).endExCommand();
    }

    public static void enterSearch(View view) {
        enterSearch(view, false);
    }

    public static void enterSearch(View view, boolean reverse) {
        if (reverse)
            getCommandLine(view).beginReverseSearch();
        else
            getCommandLine(view).beginSearch();
    }

    public static void repeatSearch(View view) {
        repeatSearch(view, false);
    }

    public static void repeatSearch(View view, boolean reverse) {
        getCommandLine(view).repeatSearch(reverse);
    }

    public static NvimulatorCommandLine getCommandLine(View view) {
        return (NvimulatorCommandLine) commandLines.get(view);
    }



    // private members
    private static NvimulatorInputHandler rootHandler = new NvimulatorInputHandler(null);

    private static Hashtable commandLines = new Hashtable();
    private static Hashtable handlers = new Hashtable();


    private static void updateBufferStatus(Buffer buffer, String status, List<View> views) {
        if (views == null)
            views = jEdit.getViewManager().getViews();

        for (int i = 0; i < views.size(); ++i) {
            if (!emulationActive(views.get(i)))
                continue;

            if (views.get(i).getBuffer() == buffer) {
                views.get(i).getStatus().setMessage(status);
            }
        }
    }

    private void initKeyBindings() {
        for(int mode : NvimulatorConstants.editorModes){
            // init global keybindings for all modes
            initKeyBindings("global", mode);
            // init visual bindings for all modes that have the visual flag
            if ((NvimulatorConstants.VISUAL & mode) != 0)
                initKeyBindings("visual", mode);
        }
        initKeyBindings("insert", NvimulatorConstants.INSERT);
        initKeyBindings("command", NvimulatorConstants.COMMAND);
        initKeyBindings("visual-block", NvimulatorConstants.VISUAL_BLOCK);
        initKeyBindings("visual-line", NvimulatorConstants.VISUAL_LINE);
    }

    private void initKeyBindings(String setPrefix, int mode) {
        String prefix = "nvimulator.keys." + setPrefix + ".";

        for (String actionName : jEdit.getActionNames()) {
            EditAction action = jEdit.getAction(actionName);

            String propName = null;
            int propIndex = 0;
            String prop = null;

            while (true) {
                propName = prefix + actionName;
                if (++propIndex >= 2)
                    propName = propName + "." + propIndex;
                prop = jEdit.getProperty(propName);

                if (prop == null)
                    break;

                rootHandler.addKeyBinding(prop, action, mode);
            }
        }
    }

    class NvimulatorListener implements CaretListener, KeyListener {
        NvimulatorListener(View view, JEditTextArea textArea) {
            this.view = view;
            this.textArea = textArea;
        }

        private void fixPosition(){
            // Dont change anything if the emulation is inactive
            if (!NvimulatorUtilities.checkEmulation(view))
                return;

            int lastAllowed = NvimulatorUtilities.getLastAllowedOffset(view, textArea);
            if (textArea.getCaretPosition() > lastAllowed)
                textArea.moveCaretPosition(lastAllowed);
        }

        public void caretUpdate(CaretEvent e) {
            fixPosition();
        }

        private View view;
        private JEditTextArea textArea;

        @Override
        public void keyTyped(KeyEvent e) {
            // Auto-generated method stub
        }

        @Override
        public void keyPressed(KeyEvent e) {
            // Auto-generated method stub
        }

        @Override
        public void keyReleased(KeyEvent e) {
            fixPosition();
        }
    }

}
