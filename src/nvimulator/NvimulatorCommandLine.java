/*
 * NvimulatorCommandLine.java - Nvimulator command entry field Copyright (C) 2002 mike dillon
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

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.search.*;
import org.gjt.sp.util.Log;

import nvimulator.ex.*;

public class NvimulatorCommandLine extends JPanel implements DocumentListener, ActionListener {
    /** The last search, for n / N; null until the first search of this session. */
    private String lastSearchRegex;
    private boolean lastSearchReverse;

    public NvimulatorCommandLine(View view) {
        super(new BorderLayout());

        this.view = view;

        setBorder(BorderFactory.createLoweredBevelBorder());

        statusLabel = new JLabel(NvimulatorPlugin.getVersionMessage());

        textField = new HistoryTextField("nvimulator");
        // textField.getDocument().addDocumentListener(this);
        // textField.addActionListener(this);

        setComponent(statusLabel);
    }

    public void setStatus(String message) {
        statusLabel.setText(message);
        setComponent(statusLabel);
    }

    public void beginExCommand() {
        beginExCommand("");
    }

    /** Opens the ex prompt with {@code initial} already typed (e.g. "'<,'>" from visual mode). */
    public void beginExCommand(String initial) {
        textField.setModel("nvimulator");
        beginCommandInput(":");
        if (!initial.isEmpty()) textField.setText(":" + initial);
    }

    public void endExCommand() {
        endCommandInput("");
    }

    public void beginSearch() {
        textField.setModel("nvimulator-search");
        beginCommandInput("/");
    }

    public void endSearch() {
        endCommandInput("");
    }

    /** Repeats the last search (n / N); {@code opposite} flips its direction. */
    public void repeatSearch(boolean opposite) {
        if (lastSearchRegex == null) {
            Registers.Register reg = Registers.getRegister('/');
            if (reg == null || reg.toString() == null || reg.toString().isEmpty()) {
                NvimulatorUtilities.beep(view);
                return;
            }
            lastSearchRegex = reg.toString();
            lastSearchReverse = false;
        }
        find(lastSearchRegex, lastSearchReverse ^ opposite);
    }

    /** Searches a Java regex and remembers it (and its direction) for n / N. Used by * and #. */
    public void search(String javaRegex, boolean reverse) {
        lastSearchRegex = javaRegex;
        lastSearchReverse = reverse;
        Registers.setRegister('/', javaRegex);
        find(javaRegex, reverse);
    }

    /** Finds the next match, skipping the one under the caret, and leaves the caret at its start. */
    private void find(String javaRegex, boolean reverse) {
        org.gjt.sp.jedit.textarea.JEditTextArea textArea = view.getTextArea();
        int caret = textArea.getCaretPosition();
        try {
            PatternSearchMatcher matcher = new PatternSearchMatcher(
                    java.util.regex.Pattern.compile(javaRegex, java.util.regex.Pattern.MULTILINE), false, false);
            SearchAndReplace.setSearchMatcher(matcher);
            SearchAndReplace.setSearchFileSet(new CurrentBufferSet());
            SearchAndReplace.setReverseSearch(reverse);
            if (!reverse && caret < textArea.getBufferLength()) textArea.setCaretPosition(caret + 1);
            if (SearchAndReplace.find(view)) {
                org.gjt.sp.jedit.textarea.Selection[] sel = textArea.getSelection();
                int start = sel.length > 0 ? sel[0].getStart() : textArea.getCaretPosition();
                textArea.setCaretPosition(start);
            } else {
                textArea.setCaretPosition(caret);
            }
        } catch (Exception e) {
            textArea.setCaretPosition(caret);
            NvimulatorUtilities.beep(view);
        }
    }

    public void beginReverseSearch() {
        textField.setModel("vimualtor-search");
        beginCommandInput("?");
    }

    public void beginShellCommand() {
        if (jEdit.getPlugin("console.ConsolePlugin") == null) {
            NvimulatorUtilities.beep(view);
            return;
        }

        beginCommandInput("!");
    }

    public Dimension getMinimumSize() {
        return textField.getMinimumSize();
    }

    public Dimension getPreferredSize() {
        return textField.getPreferredSize();
    }

    public Dimension getMaximumSize() {
        return textField.getMaximumSize();
    }

    // ActionListener
    public void actionPerformed(ActionEvent e) {
        try {
            Document doc = textField.getDocument();
            processCommand(doc.getText(0, doc.getLength()));
        } catch (BadLocationException ble) {
        }
    }

    // DocumentListener
    public void changedUpdate(DocumentEvent e) {
    }

    public void removeUpdate(DocumentEvent e) {
        if (e.getDocument().getLength() == 0)
            endCommandInput(null);
    }

    public void insertUpdate(DocumentEvent e) {
    }

    public String getPrefix() {
        return prefix;
    }

    // private members
    private View view;

    private String prefix;

    private Component comp;
    private JLabel statusLabel;
    private HistoryTextField textField;

    private void setComponent(Component comp) {
        if (this.comp == comp)
            return;

        if (this.comp != null)
            remove(this.comp);
        this.comp = comp;

        add(this.comp, BorderLayout.CENTER);

        if (this.getParent() != null) {
            revalidate();
            repaint();
        }
    }

    private void beginCommandInput(String prefix) {
        this.prefix = prefix;

        textField.setText(prefix);
        textField.getDocument().addDocumentListener(this);
        textField.addActionListener(this);

        setComponent(textField);
        textField.requestFocus();
    }

    private void endCommandInput(String message) {
        // prefix = null;

        textField.getDocument().removeDocumentListener(this);
        textField.removeActionListener(this);

        setStatus(message);
        view.getTextArea().requestFocus();
    }

    private void processCommand(String command) {
        endCommandInput(null);

        command = command.substring(prefix.length());

        if (":".equals(prefix)) {
            processExCommand(command);
        } else if ("/".equals(prefix) || "?".equals(prefix)) {
            processSearch(command);
        } else if ("!".equals(prefix)) {
            processShellCommand(command);
        } else {
            throw new InternalError("Bad command prefix: " + prefix);
        }
    }

    private void processExCommand(String expr) {
        try {
            ExInterpreter.eval(view, expr);
        } catch (BadExCommandException e) {
            Object[] args = new Object[] {expr};
            setStatus(jEdit.getProperty("nvimulator.msg.bad-ex-command", args));
        }
    }

    private void processSearch(String expr) {
        this.processSearch(expr, false);
    }

    private void processSearch(String expr, boolean reverse) {
        if ("".equals(expr)) {
            repeatSearch(reverse);
            return;
        }

        char delim = prefix.charAt(0); // prefix is '/' or '?'
        reverse = (delim == '?') ^ reverse; // if delim = ? the search direction is inverted
        String pattern = ViSearchMatcher.extractRegexString(expr, delim);
        search(pattern, reverse);
    }

    private void processShellCommand(String command) {
        setStatus(prefix + command);
    }

    private boolean getVIMode() {
        return (NvimulatorUtilities.getMode(view) & NvimulatorConstants.EX) == 0;
    }

    private void setVIMode(boolean mode) {
        if (!(getVIMode() ^ mode))
            return;
    }

    private void enterEXMode(String initial) {
        if (!getVIMode())
            return;
    }
}
