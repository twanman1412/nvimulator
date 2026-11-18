package vimulator.ex;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.textarea.JEditTextArea;

import vimulator.VimulatorCommandLine;
import vimulator.VimulatorPlugin;
import vimulator.VimulatorUtilities;
import vimulator.core.Engine;
import vimulator.core.ex.ExRange;
import vimulator.core.ex.Substitute;
import vimulator.core.ex.SubstituteCommand;
import vimulator.core.ex.VimRegex;
import vimulator.inputhandler.VimulatorInputHandler;

public class ExInterpreter {
    public static void bind(String command, String action) {
        commands.put(command, action);
    }

    public static void eval(View view, String expr) throws BadExCommandException {
        String trimmed = expr.trim();
        JEditTextArea ta = view.getTextArea();
        ExRange range;
        try {
            range = ExRange.parse(trimmed, ta.getCaretLine(), ta.getLineCount() - 1, visualLines(view));
        } catch (IllegalArgumentException e) {
            throw new BadExCommandException(view, trimmed);
        }
        boolean addressed = !range.rest().equals(trimmed);
        String command = range.rest();

        if (addressed && command.isEmpty()) {
            ta.setCaretPosition(ta.getLineStartOffset(range.last()));
            ta.goToStartOfWhiteSpace(false);
            return;
        }
        SubstituteCommand sub = SubstituteCommand.parse(command);
        if (sub != null) {
            substitute(view, range, sub);
            return;
        }
        if (command.equals("noh") || command.equals("nohlsearch"))
            return;
        if (addressed)
            throw new BadExCommandException(view, trimmed);

        EditAction action;
        Object binding = commands.get(command);

        if (binding != null) {
            try {
                action = (EditAction) binding;
            } catch (ClassCastException e) {
                action = jEdit.getAction((String) binding);
            }

            if (action != null) {
                action.invoke(view);
                return;
            }
        }

        try {
            JEditTextArea textArea = view.getTextArea();
            int line = Integer.parseInt(command);

            if (line < 1)
                line = 1;
            else if (line > textArea.getLineCount())
                line = textArea.getLineCount();

            textArea.setCaretPosition(textArea.getLineStartOffset(line - 1));
            textArea.goToStartOfWhiteSpace(false);
            return;
        } catch (NumberFormatException e) {
        }

        action = jEdit.getAction(command);
        if (action != null) {
            action.invoke(view);
            return;
        }

        throw new BadExCommandException(view, command);
    }

    private static int[] visualLines(View view) {
        if (!(view.getInputHandler() instanceof VimulatorInputHandler)) return null;
        Engine engine = ((VimulatorInputHandler) view.getInputHandler()).getEngine();
        return engine == null ? null : engine.lastVisualLines();
    }

    /** {@code :[range]s/pattern/replacement/[flags]} as one undo unit. */
    private static void substitute(View view, ExRange range, SubstituteCommand sub) {
        VimulatorCommandLine commandLine = VimulatorPlugin.getCommandLine(view);
        String regex;
        if (sub.pattern().isEmpty()) {
            Registers.Register reg = Registers.getRegister('/');
            if (reg == null || reg.toString() == null || reg.toString().isEmpty()) {
                commandLine.setStatus("No previous search pattern");
                VimulatorUtilities.beep(view);
                return;
            }
            regex = reg.toString();
        } else {
            regex = VimRegex.toJava(sub.pattern());
        }
        Buffer buffer = view.getBuffer();
        List<String> lines = new ArrayList<>();
        for (int l = range.first(); l <= range.last(); l++) lines.add(buffer.getLineText(l));
        Substitute.Result res;
        try {
            res = Substitute.apply(lines, 0, lines.size() - 1, regex,
                    VimRegex.replacementToJava(sub.replacement()), sub.global(), sub.ignoreCase());
        } catch (RuntimeException e) {
            commandLine.setStatus("Invalid pattern: " + sub.pattern());
            VimulatorUtilities.beep(view);
            return;
        }
        if (res.substitutions() == 0) {
            commandLine.setStatus("Pattern not found: " + sub.pattern());
            VimulatorUtilities.beep(view);
            return;
        }
        int extraLines = 0;
        buffer.beginCompoundEdit();
        try {
            // bottom-up, so earlier offsets stay valid
            for (int i = lines.size() - 1; i >= 0; i--) {
                String now = res.lines().get(i);
                if (now.equals(lines.get(i))) continue;
                int l = range.first() + i;
                int start = buffer.getLineStartOffset(l), end = buffer.getLineEndOffset(l) - 1;
                buffer.remove(start, end - start);
                buffer.insert(start, now);
                if (i < res.lastChangedLine()) extraLines += now.chars().filter(ch -> ch == '\n').count();
            }
        } finally {
            buffer.endCompoundEdit();
        }
        JEditTextArea ta = view.getTextArea();
        int line = Math.min(range.first() + res.lastChangedLine() + extraLines, ta.getLineCount() - 1);
        ta.setCaretPosition(ta.getLineStartOffset(line));
        ta.goToStartOfWhiteSpace(false);
        commandLine.setStatus(res.substitutions() + " substitutions on " + res.linesChanged() + " lines");
    }

    private static Hashtable commands;

    static {
        commands = new Hashtable();

        bind("w", "save");
        bind("wa", "save-all");
        bind("wq", "vi-save-close-buffer");
        bind("wq!", "vi-save-close-buffer-force");
        bind("wqa", "vi-save-close-all");
        bind("wqa!", "vi-save-close-all-force");

        bind("x", "vi-save-close-buffer");

        bind("q", "close-buffer");
        bind("q!", "vi-close-buffer-force");
        bind("qa", "close-all");
        bind("qa!", "vi-close-all-force");

        bind("bn", "next-buffer");
        bind("bp", "prev-buffer");

        bind("e!", "reload");
        // Vimulator extension
        bind("ea!", "reload-all");

        bind("reg", "view-registers");
        bind("registers", "view-registers");

        // TODO introduce search+replace
    }

    private static void parse(String expr) {
        for (int i = 0; i < expr.length(); i++) {
            // TODO
        }
    }
}
