package vimulator.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vim command engine: parses key tokens into {@code [count] (motion | operator [count]
 * (motion | text-object | operator-again) | command)} and executes them on an {@link Editor}.
 *
 * <p>{@link #feed(String)} returns REJECTED, without side effects, when a sequence does not
 * belong to the engine; the caller then replays every key since the last DONE through its own
 * bindings.
 */
public final class Engine {
    public enum Result { PENDING, DONE, REJECTED }

    public static final int MAX_COUNT = 999999;

    @FunctionalInterface
    interface MotionFn {
        int target(Ctx c);
    }

    /** Everything a motion needs. {@code arg} is the char argument of f/t/`/' motions. */
    record Ctx(Editor ed, CharClass cc, int caret, int count, boolean hasCount, int desiredCol,
               char arg, boolean forOperator) {
    }

    record MotionSpec(MotionFn fn, MotionType type, boolean keepsColumn, boolean toEol) {
    }

    /** A fully parsed command. Unused fields are null / 0. */
    record Command(int count1, int count2, String opKey, boolean doubled, String motionKey,
                   String objectKey, String cmdKey, char arg) {
        int count() {
            long n = (long) Math.max(1, count1) * Math.max(1, count2);
            return (int) Math.min(n, MAX_COUNT);
        }

        boolean hasCount() {
            return count1 > 0 || count2 > 0;
        }
    }

    private enum Kind { PENDING, REJECT, CANCEL, COMMAND }

    private record Parse(Kind kind, Command cmd, boolean beep) {
        static final Parse PENDING = new Parse(Kind.PENDING, null, false);
        static final Parse REJECT = new Parse(Kind.REJECT, null, false);
        static final Parse CANCEL_SILENT = new Parse(Kind.CANCEL, null, false);
        static final Parse CANCEL_BEEP = new Parse(Kind.CANCEL, null, true);

        static Parse of(Command c) {
            return new Parse(Kind.COMMAND, c, false);
        }
    }

    /** Token aliases, resolved before table lookup. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("<Left>", "h"), Map.entry("<BS>", "h"), Map.entry("<C-h>", "h"),
            Map.entry("<Right>", "l"), Map.entry("<Space>", "l"),
            Map.entry("<Down>", "j"), Map.entry("<C-j>", "j"), Map.entry("<C-n>", "j"),
            Map.entry("<Up>", "k"), Map.entry("<C-p>", "k"),
            Map.entry("<Home>", "0"), Map.entry("<End>", "$"),
            Map.entry("<CR>", "+"), Map.entry("<C-m>", "+"));

    private final Editor ed;
    private final List<String> pending = new ArrayList<>();
    private final Map<String, MotionSpec> motions = new HashMap<>();

    /** Column vertical motions aim for; Integer.MAX_VALUE means "end of line". */
    int desiredCol;
    private int lastEngineCaret = -1;

    public Engine(Editor ed) {
        this.ed = ed;
        registerBasicMotions();
    }

    // ------------------------------------------------------------------ public API

    public Result feed(String token) {
        if (pending.isEmpty() && ed.caret() != lastEngineCaret) {
            // the caret was moved by someone else (mouse, legacy action): resync curswant
            desiredCol = Motions.column(ed, ed.caret());
        }
        pending.add(token);
        if (token.equals("<Esc>") && pending.size() > 1) {
            pending.clear();
            finish();
            return Result.DONE;
        }
        Parse p = parse(pending);
        switch (p.kind) {
            case PENDING:
                return Result.PENDING;
            case REJECT:
                pending.clear();
                return Result.REJECTED;
            case CANCEL:
                pending.clear();
                if (p.beep) ed.beep();
                finish();
                return Result.DONE;
            default:
                pending.clear();
                execute(p.cmd);
                finish();
                return Result.DONE;
        }
    }

    public boolean isIdle() {
        return pending.isEmpty();
    }

    /** Called by the host when insert/replace mode ends, before the caret moves left. */
    public void onInsertExit() {
    }

    /** {first, last} lines of the last visual selection, or null. */
    public int[] lastVisualLines() {
        return null;
    }

    // ------------------------------------------------------------------ parsing

    /** Cursor over the pending tokens. */
    private final class Tokens {
        final List<String> t;
        int pos;

        Tokens(List<String> t) {
            this.t = t;
        }

        boolean end() {
            return pos >= t.size();
        }

        String peek() {
            return t.get(pos);
        }

        String next() {
            return t.get(pos++);
        }

        /** Parses an optional count; returns 0 for none. '0' only continues a count. */
        int count() {
            int n = 0;
            while (!end()) {
                String s = peek();
                if (s.length() != 1 || !Character.isDigit(s.charAt(0)) || (n == 0 && s.equals("0")))
                    break;
                pos++;
                n = (int) Math.min((long) n * 10 + (s.charAt(0) - '0'), MAX_COUNT);
            }
            return n;
        }

        /** Reads a key, joining g/z prefixes; null when more tokens are needed. */
        String key() {
            String tok = next();
            if (tok.equals("g") || tok.equals("z")) {
                if (end()) return null;
                return tok + next();
            }
            return ALIASES.getOrDefault(tok, tok);
        }
    }

    private Parse parse(List<String> list) {
        Tokens t = new Tokens(list);
        int count1 = t.count();
        if (t.end()) return Parse.PENDING;
        String key = t.key();
        if (key == null) return Parse.PENDING;

        MotionSpec m = motions.get(key);
        if (m != null) {
            return Parse.of(new Command(count1, 0, null, false, key, null, null, '\0'));
        }
        return Parse.REJECT;
    }

    // ------------------------------------------------------------------ execution

    private void execute(Command c) {
        if (c.motionKey != null) {
            executeMotion(c);
        }
    }

    private CharClass charClass() {
        return new CharClass(ed.extraWordChars());
    }

    private Ctx ctx(Command c, boolean forOperator) {
        return new Ctx(ed, charClass(), ed.caret(), c.count(), c.hasCount(), desiredCol, c.arg, forOperator);
    }

    private void executeMotion(Command c) {
        MotionSpec s = motions.get(c.motionKey);
        int target = s.fn.target(ctx(c, false));
        if (target < 0) {
            ed.beep();
            return;
        }
        moveTo(target, s);
    }

    private void moveTo(int target, MotionSpec s) {
        int pos = Motions.clampNormal(ed, target);
        ed.setCaret(pos);
        if (s.toEol) desiredCol = Integer.MAX_VALUE;
        else if (!s.keepsColumn) desiredCol = Motions.column(ed, pos);
    }

    /** Post-command bookkeeping: clamp the caret in normal mode and remember where it is. */
    private void finish() {
        if (ed.mode() == Mode.NORMAL) {
            int c = Motions.clampNormal(ed, ed.caret());
            if (c != ed.caret()) ed.setCaret(c);
        }
        lastEngineCaret = ed.caret();
    }

    // ------------------------------------------------------------------ motion table

    private void motion(String key, MotionType type, MotionFn fn) {
        motions.put(key, new MotionSpec(fn, type, false, false));
    }

    private void registerBasicMotions() {
        motion("h", MotionType.EXCLUSIVE, c -> Motions.left(c.ed, c.caret, c.count));
        motion("l", MotionType.EXCLUSIVE, c -> Motions.right(c.ed, c.caret, c.count, c.forOperator));
        motions.put("j", new MotionSpec(c -> Motions.down(c.ed, c.caret, c.count, c.desiredCol),
                MotionType.LINEWISE, true, false));
        motions.put("k", new MotionSpec(c -> Motions.up(c.ed, c.caret, c.count, c.desiredCol),
                MotionType.LINEWISE, true, false));
        motion("0", MotionType.EXCLUSIVE, c -> Motions.lineHome(c.ed, c.caret));
        motion("^", MotionType.EXCLUSIVE, c -> Motions.firstNonBlank(c.ed, c.caret, c.count));
        motions.put("$", new MotionSpec(c -> Motions.lineEnd(c.ed, c.caret, c.count),
                MotionType.INCLUSIVE, false, true));
        motion("+", MotionType.LINEWISE, c -> Motions.nextLineStart(c.ed, c.caret, c.count));
        motion("-", MotionType.LINEWISE, c -> Motions.prevLineStart(c.ed, c.caret, c.count));
        motion("_", MotionType.LINEWISE, c -> Motions.underscore(c.ed, c.caret, c.count));
        motion("gg", MotionType.LINEWISE, c -> Motions.goToLine(c.ed, c.hasCount ? c.count : 1));
        motion("G", MotionType.LINEWISE, c -> Motions.goToLine(c.ed, c.hasCount ? c.count : c.ed.lineCount()));
    }
}
