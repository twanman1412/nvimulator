package vimulator.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

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

    /** {@code dynType}, when set, decides the motion type per command (e.g. {@code ;} after F vs f). */
    record MotionSpec(MotionFn fn, MotionType type, boolean keepsColumn, boolean toEol,
                      Function<Command, MotionType> dynType) {
        MotionType typeFor(Command c) {
            return dynType != null ? dynType.apply(c) : type;
        }
    }

    /** A non-motion command; {@code needsArg} commands take the next key as a char argument. */
    record CommandSpec(boolean needsArg, Consumer<Command> run) {
    }

    record LastFind(char ch, boolean backward, boolean till) {
    }

    /** Motions whose next key is a character argument. */
    private static final Set<String> ARG_MOTIONS = Set.of("f", "F", "t", "T", "`", "'");

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
    private final Map<String, CommandSpec> commands = new HashMap<>();
    private LastFind lastFind;
    private final Map<Character, int[]> marks = new HashMap<>();

    /** Column vertical motions aim for; Integer.MAX_VALUE means "end of line". */
    int desiredCol;
    private int lastEngineCaret = -1;

    public Engine(Editor ed) {
        this.ed = ed;
        registerBasicMotions();
        registerFindMotions();
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
            char arg = '\0';
            if (ARG_MOTIONS.contains(key)) {
                Parse bad = argProblem(t);
                if (bad != null) return bad;
                arg = argChar(t.next());
            }
            return Parse.of(new Command(count1, 0, null, false, key, null, null, arg));
        }
        CommandSpec cs = commands.get(key);
        if (cs != null) {
            char arg = '\0';
            if (cs.needsArg) {
                Parse bad = argProblem(t);
                if (bad != null) return bad;
                arg = argChar(t.next());
            }
            return Parse.of(new Command(count1, 0, null, false, null, null, key, arg));
        }
        return Parse.REJECT;
    }

    /** null when the next token is a usable char argument, otherwise how to handle it. */
    private static Parse argProblem(Tokens t) {
        if (t.end()) return Parse.PENDING;
        String tok = t.peek();
        if (tok.length() == 1 || tok.equals("<Space>") || tok.equals("<Tab>")) return null;
        return tok.equals("<Esc>") ? Parse.CANCEL_SILENT : Parse.CANCEL_BEEP;
    }

    private static char argChar(String tok) {
        if (tok.equals("<Space>")) return ' ';
        if (tok.equals("<Tab>")) return '\t';
        return tok.charAt(0);
    }

    // ------------------------------------------------------------------ execution

    private void execute(Command c) {
        if (c.motionKey != null) {
            executeMotion(c);
        } else if (c.cmdKey != null) {
            commands.get(c.cmdKey).run.accept(c);
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
        motions.put(key, new MotionSpec(fn, type, false, false, null));
    }

    private void registerBasicMotions() {
        motion("h", MotionType.EXCLUSIVE, c -> Motions.left(c.ed, c.caret, c.count));
        motion("l", MotionType.EXCLUSIVE, c -> Motions.right(c.ed, c.caret, c.count, c.forOperator));
        motions.put("j", new MotionSpec(c -> Motions.down(c.ed, c.caret, c.count, c.desiredCol),
                MotionType.LINEWISE, true, false, null));
        motions.put("k", new MotionSpec(c -> Motions.up(c.ed, c.caret, c.count, c.desiredCol),
                MotionType.LINEWISE, true, false, null));
        motion("0", MotionType.EXCLUSIVE, c -> Motions.lineHome(c.ed, c.caret));
        motion("^", MotionType.EXCLUSIVE, c -> Motions.firstNonBlank(c.ed, c.caret, c.count));
        motions.put("$", new MotionSpec(c -> Motions.lineEnd(c.ed, c.caret, c.count),
                MotionType.INCLUSIVE, false, true, null));
        motion("+", MotionType.LINEWISE, c -> Motions.nextLineStart(c.ed, c.caret, c.count));
        motion("-", MotionType.LINEWISE, c -> Motions.prevLineStart(c.ed, c.caret, c.count));
        motion("_", MotionType.LINEWISE, c -> Motions.underscore(c.ed, c.caret, c.count));
        motion("gg", MotionType.LINEWISE, c -> Motions.goToLine(c.ed, c.hasCount ? c.count : 1));
        motion("G", MotionType.LINEWISE, c -> Motions.goToLine(c.ed, c.hasCount ? c.count : c.ed.lineCount()));

        motion("w", MotionType.EXCLUSIVE, c -> Motions.wordForward(c.ed, c.cc, c.caret, c.count, false));
        motion("W", MotionType.EXCLUSIVE, c -> Motions.wordForward(c.ed, c.cc, c.caret, c.count, true));
        motion("b", MotionType.EXCLUSIVE, c -> Motions.wordBackward(c.ed, c.cc, c.caret, c.count, false));
        motion("B", MotionType.EXCLUSIVE, c -> Motions.wordBackward(c.ed, c.cc, c.caret, c.count, true));
        motion("e", MotionType.INCLUSIVE, c -> Motions.wordEnd(c.ed, c.cc, c.caret, c.count, false));
        motion("E", MotionType.INCLUSIVE, c -> Motions.wordEnd(c.ed, c.cc, c.caret, c.count, true));
        motion("ge", MotionType.INCLUSIVE, c -> Motions.wordEndBackward(c.ed, c.cc, c.caret, c.count, false));
        motion("gE", MotionType.INCLUSIVE, c -> Motions.wordEndBackward(c.ed, c.cc, c.caret, c.count, true));
        motion("}", MotionType.EXCLUSIVE, c -> Motions.paragraphForward(c.ed, c.caret, c.count));
        motion("{", MotionType.EXCLUSIVE, c -> Motions.paragraphBackward(c.ed, c.caret, c.count));
    }

    private int find(Ctx c, char ch, boolean backward, boolean till, boolean repeat) {
        return Motions.findChar(c.ed, c.caret, c.count, ch, backward, till, repeat);
    }

    private int findAndRemember(Ctx c, boolean backward, boolean till) {
        lastFind = new LastFind(c.arg, backward, till);
        return find(c, c.arg, backward, till, false);
    }

    private int repeatFind(Ctx c, boolean reverse) {
        if (lastFind == null) return -1;
        return find(c, lastFind.ch, lastFind.backward ^ reverse, lastFind.till, true);
    }

    private MotionType repeatType(boolean reverse) {
        boolean backward = lastFind != null && (lastFind.backward ^ reverse);
        return backward ? MotionType.EXCLUSIVE : MotionType.INCLUSIVE;
    }

    private int markTarget(Ctx c, boolean exact) {
        int[] m = marks.get(c.arg);
        if (m == null) return -1;
        int line = Math.min(m[0], c.ed.lineCount() - 1);
        if (!exact) return Motions.firstNonBlank(c.ed, line);
        int s = c.ed.lineStart(line);
        return Math.min(s + m[1], Motions.lastChar(c.ed, line));
    }

    private void registerFindMotions() {
        motion("f", MotionType.INCLUSIVE, c -> findAndRemember(c, false, false));
        motion("t", MotionType.INCLUSIVE, c -> findAndRemember(c, false, true));
        motion("F", MotionType.EXCLUSIVE, c -> findAndRemember(c, true, false));
        motion("T", MotionType.EXCLUSIVE, c -> findAndRemember(c, true, true));
        motions.put(";", new MotionSpec(c -> repeatFind(c, false), MotionType.INCLUSIVE, false, false,
                cmd -> repeatType(false)));
        motions.put(",", new MotionSpec(c -> repeatFind(c, true), MotionType.INCLUSIVE, false, false,
                cmd -> repeatType(true)));
        motions.put("%", new MotionSpec(
                c -> c.hasCount ? Motions.percentLine(c.ed, c.count) : Motions.matchBracket(c.ed, c.caret),
                MotionType.INCLUSIVE, false, false,
                cmd -> cmd.hasCount() ? MotionType.LINEWISE : MotionType.INCLUSIVE));
        motion("`", MotionType.EXCLUSIVE, c -> markTarget(c, true));
        motion("'", MotionType.LINEWISE, c -> markTarget(c, false));
        commands.put("m", new CommandSpec(true, c -> {
            if (c.arg < 'a' || c.arg > 'z') {
                ed.beep();
                return;
            }
            int caret = ed.caret();
            marks.put(c.arg, new int[] {ed.lineOfOffset(caret), Motions.column(ed, caret)});
        }));
    }
}
