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

    @FunctionalInterface
    interface TextObjectFn {
        Range select(Ctx c);
    }

    /** Operator keys. */
    static final Map<String, Operator> OPERATORS = Map.of(
            "d", Operator.DELETE, "c", Operator.CHANGE, "y", Operator.YANK,
            ">", Operator.SHIFT_RIGHT, "<", Operator.SHIFT_LEFT, "=", Operator.REINDENT,
            "g~", Operator.TOGGLE_CASE, "gu", Operator.LOWERCASE, "gU", Operator.UPPERCASE);

    /** Shorthands parsed as an operator plus a motion; a null motion means the doubled (linewise) form. */
    private static final Map<String, String[]> OP_ALIASES = Map.of(
            "x", new String[] {"d", "l"}, "X", new String[] {"d", "h"},
            "D", new String[] {"d", "$"}, "C", new String[] {"c", "$"},
            "s", new String[] {"c", "l"}, "S", new String[] {"c", null},
            "Y", new String[] {"y", null});

    /** Keys that apply an operator to the selection in visual modes, mapped to the operator key. */
    private static final Map<String, String> VISUAL_OPS = Map.ofEntries(
            Map.entry("d", "d"), Map.entry("x", "d"), Map.entry("c", "c"), Map.entry("s", "c"),
            Map.entry("y", "y"), Map.entry(">", ">"), Map.entry("<", "<"), Map.entry("=", "="),
            Map.entry("~", "g~"), Map.entry("u", "gu"), Map.entry("U", "gU"),
            Map.entry("g~", "g~"), Map.entry("gu", "gu"), Map.entry("gU", "gU"));

    record Visual(int anchor, int caret, Mode mode) {
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
    /** Text objects ("iw", "a(", ...), registered in Task 7. */
    private final Map<String, TextObjectFn> textObjects = new HashMap<>();
    /** The last command that changed text, for {@code .}. */
    Command lastChange;
    private final Map<String, CommandSpec> visualCommands = new HashMap<>();
    /** Visual anchor; -1 when unknown (then the caret is used). */
    private int anchor = -1;
    private Visual lastVisual;
    /** Insert recording for {@code .}: active from a change entering insert mode until it ends. */
    private boolean recording;
    private int insertStart;
    /** Text typed during the insert that belongs to {@link #lastChange}. */
    private String lastInsertedText = "";
    private boolean replaying;
    /** An undo unit opened by a change that entered insert mode, closed in {@link #onInsertExit}. */
    private boolean insertEditOpen;
    /** Normal-mode commands that change text (besides operators). */
    private static final Set<String> CHANGE_COMMANDS =
            Set.of("~", "J", "gJ", "<C-a>", "<C-x>", "r", "i", "a", "I", "A", "o", "O", ".");
    /** Insert commands whose count repeats the typed text. */
    private static final Set<String> INSERT_COMMANDS = Set.of("i", "a", "I", "A", "o", "O");

    /** Lines scrolled by C-d / C-u; 0 means half a screen. */
    private int scrollAmount;

    /** Column vertical motions aim for; Integer.MAX_VALUE means "end of line". */
    int desiredCol;
    private int lastEngineCaret = -1;

    public Engine(Editor ed) {
        this.ed = ed;
        registerBasicMotions();
        registerFindMotions();
        registerTextObjects();
        registerEditCommands();
        registerVisualCommands();
        registerScrolling();
        registerInsertCommands();
        registerStarSearch();
    }

    // ------------------------------------------------------------------ public API

    public Result feed(String token) {
        if (pending.isEmpty() && !ed.mode().isVisual()) anchor = -1;
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
                boolean wasVisual = ed.mode().isVisual();
                boolean keepOpen = false;
                ed.beginEdit();
                try {
                    execute(p.cmd);
                    // a change that enters insert mode stays one undo unit with the typed text
                    keepOpen = isInsert(ed.mode());
                } finally {
                    if (keepOpen) insertEditOpen = true;
                    else ed.endEdit();
                }
                if (!replaying && !wasVisual && isInsert(ed.mode())) startRecording(p.cmd);
                finish();
                return Result.DONE;
        }
    }

    public boolean isIdle() {
        return pending.isEmpty();
    }

    /** True after a {@code $}-style motion: the caret wants to be at the end of the line. */
    public boolean wantsLineEnd() {
        return desiredCol == Integer.MAX_VALUE;
    }

    /** Called by the host when insert/replace mode ends, before the caret moves left. */
    public void onInsertExit() {
        try {
            recordInsert();
        } finally {
            if (insertEditOpen) {
                insertEditOpen = false;
                ed.endEdit();
            }
        }
    }

    private void recordInsert() {
        if (!recording) return;
        recording = false;
        int caret = ed.caret();
        String text = caret >= insertStart && insertStart <= ed.length() ? ed.text(insertStart, caret) : "";
        lastInsertedText = text;
        Command c = lastChange;
        if (c != null && c.cmdKey != null && INSERT_COMMANDS.contains(c.cmdKey) && c.count() > 1 && !text.isEmpty()) {
            String more = repeatedInsert(c, text, c.count() - 1);
            ed.replace(caret, caret, more);
            ed.setCaret(caret + more.length());
        }
    }

    private static boolean isInsert(Mode m) {
        return m == Mode.INSERT || m == Mode.REPLACE;
    }

    private void startRecording(Command c) {
        recording = true;
        insertStart = ed.caret();
        lastChange = c;
        lastInsertedText = "";
    }

    /** Text added for an insert command repeated {@code times} more times (o/O open new lines). */
    private String repeatedInsert(Command c, String text, int times) {
        String chunk = c.cmdKey.equals("o") || c.cmdKey.equals("O") ? "\n" + text : text;
        return chunk.repeat(times);
    }

    /** {first, last} lines of the last visual selection, or null. */
    public int[] lastVisualLines() {
        if (lastVisual == null) return null;
        int len = ed.length();
        int a = Math.min(Math.min(lastVisual.anchor, lastVisual.caret), len);
        int b = Math.min(Math.max(lastVisual.anchor, lastVisual.caret), len);
        return new int[] {ed.lineOfOffset(a), ed.lineOfOffset(b)};
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
            return key(true);
        }

        /** @param allowZ false while an operator is pending: {@code z} is not a motion prefix there */
        String key(boolean allowZ) {
            String tok = next();
            if (tok.equals("g") || (allowZ && tok.equals("z"))) {
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
        if (ed.mode().isVisual()) return parseVisual(t, count1);
        String key = t.key();
        if (key == null) return Parse.PENDING;

        Operator op = OPERATORS.get(key);
        if (op != null) return parseOperator(t, count1, key);
        String[] alias = OP_ALIASES.get(key);
        if (alias != null) {
            return Parse.of(new Command(count1, 0, alias[0], alias[1] == null, alias[1], null, null, '\0'));
        }

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

    /** Visual modes: operators act on the selection, motions extend it, i/a select objects. */
    private Parse parseVisual(Tokens t, int count1) {
        String tok = t.peek();
        if (tok.equals("i") || tok.equals("a")) {
            t.next();
            if (t.end()) return Parse.PENDING;
            String obj = tok + t.next();
            if (!textObjects.containsKey(obj)) return Parse.CANCEL_BEEP;
            return Parse.of(new Command(count1, 0, null, false, null, obj, null, '\0'));
        }
        String key = t.key();
        if (key == null) return Parse.PENDING;
        String opKey = VISUAL_OPS.get(key);
        if (opKey != null) {
            if (ed.mode() == Mode.VISUAL_BLOCK && (opKey.equals("c") || opKey.equals("="))) return Parse.REJECT;
            return Parse.of(new Command(count1, 0, opKey, false, null, null, null, '\0'));
        }
        if (visualCommands.containsKey(key)) {
            return Parse.of(new Command(count1, 0, null, false, null, null, key, '\0'));
        }
        if (motions.containsKey(key)) {
            char arg = '\0';
            if (ARG_MOTIONS.contains(key)) {
                Parse bad = argProblem(t);
                if (bad != null) return bad;
                arg = argChar(t.next());
            }
            return Parse.of(new Command(count1, 0, null, false, key, null, null, arg));
        }
        return Parse.REJECT;
    }

    /** After an operator: [count] (motion | text-object | the operator again). */
    private Parse parseOperator(Tokens t, int count1, String opKey) {
        int count2 = t.count();
        if (t.end()) return Parse.PENDING;
        String tok = t.peek();
        if (tok.equals("i") || tok.equals("a")) {
            t.next();
            if (t.end()) return Parse.PENDING;
            String obj = tok + t.next();
            if (!textObjects.containsKey(obj)) return Parse.CANCEL_BEEP;
            return Parse.of(new Command(count1, count2, opKey, false, null, obj, null, '\0'));
        }
        String key = t.key(false);
        if (key == null) return Parse.PENDING;
        if (key.equals(opKey) || (opKey.length() == 2 && key.equals(opKey.substring(1)))) {
            return Parse.of(new Command(count1, count2, opKey, true, null, null, null, '\0'));
        }
        if (!motions.containsKey(key)) return Parse.CANCEL_BEEP;
        char arg = '\0';
        if (ARG_MOTIONS.contains(key)) {
            Parse bad = argProblem(t);
            if (bad != null) return bad;
            arg = argChar(t.next());
        }
        return Parse.of(new Command(count1, count2, opKey, false, key, null, null, arg));
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

    private boolean changesText(Command c, boolean visual) {
        if (c.opKey != null) return !c.opKey.equals("y");
        if (c.cmdKey == null) return false;
        if (visual) return c.cmdKey.equals("J") || c.cmdKey.equals("gJ");
        return CHANGE_COMMANDS.contains(c.cmdKey);
    }

    private void execute(Command c) {
        boolean visual = ed.mode().isVisual();
        if (!ed.editable() && changesText(c, visual)) {
            ed.beep();
            if (visual) {
                ed.setMode(Mode.NORMAL);
                anchor = -1;
            }
            return;
        }
        if (visual) {
            executeVisual(c);
            return;
        }
        if (c.opKey != null) {
            executeOperator(c);
        } else if (c.motionKey != null) {
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

    private void executeOperator(Command c) {
        Operator op = OPERATORS.get(c.opKey);
        Range r = operatorRange(c, op);
        if (r == null) {
            ed.beep();
            return;
        }
        boolean ok;
        ed.beginEdit();
        try {
            ok = Operators.apply(ed, op, r, charClass());
        } finally {
            ed.endEdit();
        }
        if (!ok) {
            ed.beep();
            return;
        }
        if (op != Operator.YANK) lastChange = c;
        if (ed.mode() == Mode.NORMAL) desiredCol = Motions.column(ed, Motions.clampNormal(ed, ed.caret()));
    }

    /** The range an operator acts on, with Vim's exclusive / cw / dw adjustments; null on failure. */
    private Range operatorRange(Command c, Operator op) {
        int caret = ed.caret();
        if (c.doubled) {
            int first = ed.lineOfOffset(caret);
            long last = Math.max(first, Math.min((long) first + c.count() - 1, Motions.lastLine(ed)));
            return new Range(ed.lineStart(first), ed.lineStart((int) last), MotionType.LINEWISE);
        }
        Ctx x = ctx(c, true);
        if (c.objectKey != null) return textObjects.get(c.objectKey).select(x);

        String key = c.motionKey;
        boolean word = key.equals("w") || key.equals("W");
        if (word && op == Operator.CHANGE && caret < ed.length()
                && x.cc.of(ed.charAt(caret)) != CharClass.BLANK) {
            // cw / cW: change to the end of the word, like ce / cE
            boolean big = key.equals("W");
            int end = endOfCurrentWord(x.cc, caret, big);
            if (c.count() > 1) end = Motions.wordEnd(ed, x.cc, end, c.count() - 1, big);
            if (end < 0) return null;
            return new Range(caret, end, MotionType.INCLUSIVE);
        }

        MotionSpec s = motions.get(key);
        int target = s.fn.target(x);
        if (target < 0) return null;
        MotionType type = s.typeFor(c);
        int a = Math.min(caret, target), b = Math.max(caret, target);
        if (type != MotionType.EXCLUSIVE) return new Range(a, b, type);

        int lineA = ed.lineOfOffset(a), lineB = ed.lineOfOffset(b);
        if (word && lineB > lineA) {
            // dw: when the last word moved over ends a line, stop at that line's end
            return new Range(a, ed.lineEnd(lineB - 1), MotionType.EXCLUSIVE);
        }
        if (lineB > lineA && b == ed.lineStart(lineB)) {
            // exclusive motion ending at column 0: end at the previous line's end, inclusive
            int newEnd = ed.lineEnd(lineB - 1);
            if (a <= Motions.firstNonBlank(ed, lineA))
                return new Range(a, newEnd, MotionType.LINEWISE);
            return new Range(a, newEnd, MotionType.EXCLUSIVE);
        }
        return new Range(a, b, MotionType.EXCLUSIVE);
    }

    private int endOfCurrentWord(CharClass cc, int caret, boolean big) {
        int c0 = big ? cc.ofBig(ed.charAt(caret)) : cc.of(ed.charAt(caret));
        int i = caret;
        while (i + 1 < ed.length()) {
            char ch = ed.charAt(i + 1);
            if ((big ? cc.ofBig(ch) : cc.of(ch)) != c0) break;
            i++;
        }
        return i;
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

    private void registerTextObjects() {
        textObjects.put("iw", c -> TextObjects.innerWord(c.ed, c.cc, c.caret, c.count, false));
        textObjects.put("iW", c -> TextObjects.innerWord(c.ed, c.cc, c.caret, c.count, true));
        textObjects.put("aw", c -> TextObjects.aWord(c.ed, c.cc, c.caret, c.count, false));
        textObjects.put("aW", c -> TextObjects.aWord(c.ed, c.cc, c.caret, c.count, true));
        for (char q : new char[] {'"', '\'', '`'}) {
            textObjects.put("i" + q, c -> TextObjects.quote(c.ed, c.caret, q, false));
            textObjects.put("a" + q, c -> TextObjects.quote(c.ed, c.caret, q, true));
        }
        String[][] pairs = {{"(", ")", "b"}, {"[", "]", null}, {"{", "}", "B"}, {"<", ">", null}};
        for (String[] p : pairs) {
            char open = p[0].charAt(0), close = p[1].charAt(0);
            for (String k : new String[] {p[0], p[1], p[2]}) {
                if (k == null) continue;
                textObjects.put("i" + k, c -> TextObjects.bracket(c.ed, c.caret, c.count, open, close, false));
                textObjects.put("a" + k, c -> TextObjects.bracket(c.ed, c.caret, c.count, open, close, true));
            }
        }
        textObjects.put("ip", c -> TextObjects.paragraph(c.ed, c.caret, c.count, false));
        textObjects.put("ap", c -> TextObjects.paragraph(c.ed, c.caret, c.count, true));
        textObjects.put("it", c -> TextObjects.tag(c.ed, c.caret, c.count, false));
        textObjects.put("at", c -> TextObjects.tag(c.ed, c.caret, c.count, true));
    }

    /** Runs a text-changing command as one undo unit and remembers it for {@code .}. */
    private void change(Command c, Runnable body) {
        ed.beginEdit();
        try {
            body.run();
        } finally {
            ed.endEdit();
        }
        lastChange = c;
    }

    private void registerEditCommands() {
        commands.put("~", new CommandSpec(false, c -> {
            int caret = ed.caret();
            int le = ed.lineEnd(ed.lineOfOffset(caret));
            if (caret >= le) return;
            int end = (int) Math.min((long) caret + c.count(), le);
            change(c, () -> ed.replace(caret, end, Transforms.toggleCase(ed.text(caret, end))));
            ed.setCaret(Math.min(end, Motions.lastChar(ed, ed.lineOfOffset(caret))));
            desiredCol = Motions.column(ed, ed.caret());
        }));
        commands.put("J", new CommandSpec(false, c -> join(c, true)));
        commands.put("gJ", new CommandSpec(false, c -> join(c, false)));
        commands.put("<C-a>", new CommandSpec(false, c -> increment(c, c.count())));
        commands.put("<C-x>", new CommandSpec(false, c -> increment(c, -c.count())));
        commands.put("r", new CommandSpec(true, c -> {
            int caret = ed.caret();
            long end = (long) caret + c.count();
            if (end > ed.lineEnd(ed.lineOfOffset(caret))) {
                ed.beep();
                return;
            }
            change(c, () -> ed.replace(caret, (int) end, String.valueOf(c.arg).repeat(c.count())));
            ed.setCaret((int) end - 1);
        }));
    }

    private void join(Command c, boolean spaces) {
        int line = ed.lineOfOffset(ed.caret());
        int lastLine = Motions.lastLine(ed);
        if (line >= lastLine) {
            ed.beep();
            return;
        }
        int joins = Math.min(Math.max(c.count(), 2) - 1, lastLine - line);
        int[] caret = {ed.caret()};
        change(c, () -> {
            for (int i = 0; i < joins; i++) caret[0] = Transforms.join(ed, line, spaces);
        });
        ed.setCaret(caret[0]);
        desiredCol = Motions.column(ed, caret[0]);
    }

    private void increment(Command c, int delta) {
        int[] num = Transforms.findNumber(ed, ed.caret());
        if (num == null) {
            ed.beep();
            return;
        }
        long value;
        try {
            value = Math.addExact(Long.parseLong(ed.text(num[0], num[1])), delta);
        } catch (NumberFormatException | ArithmeticException e) {
            ed.beep();
            return;
        }
        String s = Long.toString(value);
        change(c, () -> ed.replace(num[0], num[1], s));
        ed.setCaret(num[0] + s.length() - 1);
    }

    // ------------------------------------------------------------------ visual modes

    private void executeVisual(Command c) {
        if (anchor < 0 || anchor > ed.length()) anchor = ed.caret();
        if (c.objectKey != null) {
            selectObject(c);
        } else if (c.opKey != null) {
            visualOperator(c);
        } else if (c.cmdKey != null) {
            visualCommands.get(c.cmdKey).run.accept(c);
        } else {
            MotionSpec s = motions.get(c.motionKey);
            int target = s.fn.target(ctx(c, false));
            if (target < 0) {
                ed.beep();
                return;
            }
            if (s.toEol && ed.mode() != Mode.VISUAL_BLOCK) {
                // $ in visual mode selects up to and including the line break
                int line = ed.lineOfOffset(target);
                int pos = ed.lineEnd(line) < ed.length() ? ed.lineEnd(line) : Motions.lastChar(ed, line);
                ed.setCaret(pos);
                desiredCol = Integer.MAX_VALUE;
            } else {
                moveTo(target, s);
            }
            updateSelection();
        }
    }

    private void updateSelection() {
        ed.setSelection(anchor, ed.caret(), ed.mode());
        lastVisual = new Visual(anchor, ed.caret(), ed.mode());
    }

    private void enterVisual(Mode m) {
        Mode cur = ed.mode();
        if (cur == m) {
            ed.setMode(Mode.NORMAL);
            anchor = -1;
            return;
        }
        if (!cur.isVisual() || anchor < 0) anchor = ed.caret();
        ed.setMode(m);
        updateSelection();
    }

    private void selectObject(Command c) {
        Range r = textObjects.get(c.objectKey).select(ctx(c, false));
        if (r == null) {
            ed.beep();
            return;
        }
        Mode mode = ed.mode();
        int caret;
        if (r.type() == MotionType.LINEWISE) {
            if (mode != Mode.VISUAL_LINE) ed.setMode(Mode.VISUAL_LINE);
            caret = r.end();
        } else {
            if (mode == Mode.VISUAL_LINE) ed.setMode(Mode.VISUAL);
            caret = r.type() == MotionType.EXCLUSIVE ? r.start() : r.end();
        }
        anchor = r.start();
        ed.setCaret(caret);
        desiredCol = Motions.column(ed, caret);
        updateSelection();
    }

    private void visualOperator(Command c) {
        Mode mode = ed.mode();
        Operator op = OPERATORS.get(c.opKey);
        int caret = ed.caret();
        lastVisual = new Visual(anchor, caret, mode);
        if (mode == Mode.VISUAL_BLOCK) {
            ed.beginEdit();
            try {
                ed.blockOperator(op);
            } finally {
                ed.endEdit();
            }
            ed.setMode(Mode.NORMAL);
            anchor = -1;
            return;
        }
        int a = Math.min(anchor, caret), b = Math.min(Math.max(anchor, caret), Math.max(0, ed.length()));
        Range r;
        if (mode == Mode.VISUAL_LINE) {
            r = new Range(a, b, MotionType.LINEWISE);
        } else {
            // a charwise selection that covers a line end includes the line break
            boolean onLineEnd = b < ed.length() && ed.charAt(b) == '\n';
            r = new Range(a, onLineEnd ? b + 1 : Operators.inclusiveEnd(ed, b), MotionType.EXCLUSIVE);
        }
        ed.setMode(Mode.NORMAL);
        anchor = -1;
        boolean ok;
        ed.beginEdit();
        try {
            ok = Operators.apply(ed, op, r, charClass());
        } finally {
            ed.endEdit();
        }
        if (!ok) ed.beep();
    }

    private void visualJoin(boolean spaces) {
        int caret = ed.caret();
        int first = ed.lineOfOffset(Math.min(anchor, caret)), last = ed.lineOfOffset(Math.max(anchor, caret));
        lastVisual = new Visual(anchor, caret, ed.mode());
        ed.setMode(Mode.NORMAL);
        anchor = -1;
        if (first >= Motions.lastLine(ed)) {
            ed.beep();
            return;
        }
        int joins = Math.min(Math.max(last - first, 1), Motions.lastLine(ed) - first);
        int[] pos = {caret};
        ed.beginEdit();
        try {
            for (int i = 0; i < joins; i++) pos[0] = Transforms.join(ed, first, spaces);
        } finally {
            ed.endEdit();
        }
        ed.setCaret(pos[0]);
    }

    private void registerVisualCommands() {
        commands.put("v", new CommandSpec(false, c -> enterVisual(Mode.VISUAL)));
        commands.put("V", new CommandSpec(false, c -> enterVisual(Mode.VISUAL_LINE)));
        commands.put("<C-v>", new CommandSpec(false, c -> enterVisual(Mode.VISUAL_BLOCK)));
        commands.put("gv", new CommandSpec(false, c -> {
            if (lastVisual == null) {
                ed.beep();
                return;
            }
            int max = Math.max(0, ed.length() - 1);
            anchor = Math.min(lastVisual.anchor, max);
            ed.setMode(lastVisual.mode);
            ed.setCaret(Math.min(lastVisual.caret, max));
            updateSelection();
        }));
        visualCommands.put("v", new CommandSpec(false, c -> enterVisual(Mode.VISUAL)));
        visualCommands.put("V", new CommandSpec(false, c -> enterVisual(Mode.VISUAL_LINE)));
        visualCommands.put("<C-v>", new CommandSpec(false, c -> enterVisual(Mode.VISUAL_BLOCK)));
        CommandSpec swap = new CommandSpec(false, c -> {
            int caret = ed.caret();
            ed.setCaret(anchor);
            anchor = caret;
            desiredCol = Motions.column(ed, ed.caret());
            updateSelection();
        });
        visualCommands.put("o", swap);
        visualCommands.put("O", swap);
        visualCommands.put("J", new CommandSpec(false, c -> visualJoin(true)));
        visualCommands.put("gJ", new CommandSpec(false, c -> visualJoin(false)));
    }

    // ------------------------------------------------------------------ scrolling

    /** Applies {first', caret'}: scroll first, then put the caret on its line at the desired column. */
    private void scrollTo(int[] r) {
        if (r == null) {
            ed.beep();
            return;
        }
        ed.setFirstVisibleLine(r[0]);
        ed.setCaret(Motions.atColumn(ed, r[1], desiredCol));
    }

    private void scrollCommand(String key, java.util.function.Function<Command, int[]> f) {
        CommandSpec spec = new CommandSpec(false, c -> scrollTo(f.apply(c)));
        commands.put(key, spec);
        visualCommands.put(key, new CommandSpec(false, c -> {
            scrollTo(f.apply(c));
            updateSelection();
        }));
    }

    private int caretLine() {
        return ed.lineOfOffset(ed.caret());
    }

    private void zCommand(String key, char where, boolean toFirstNonBlank) {
        commands.put(key, new CommandSpec(false, c -> {
            if (c.hasCount()) {
                int line = Math.min(c.count(), ed.lineCount()) - 1;
                ed.setCaret(Motions.atColumn(ed, line, desiredCol));
            }
            int line = caretLine();
            ed.setFirstVisibleLine(Scrolling.position(ed.visibleLineCount(), line, where));
            if (toFirstNonBlank) {
                ed.setCaret(Motions.firstNonBlank(ed, line));
                desiredCol = Motions.column(ed, ed.caret());
            }
        }));
    }

    private void registerScrolling() {
        java.util.function.Function<Command, Integer> amount = c -> {
            if (c.hasCount()) scrollAmount = c.count();
            return scrollAmount > 0 ? scrollAmount : Math.max(1, ed.visibleLineCount() / 2);
        };
        scrollCommand("<C-d>", c -> Scrolling.halfPage(Motions.lastLine(ed) + 1, ed.visibleLineCount(),
                ed.firstVisibleLine(), caretLine(), amount.apply(c), true));
        scrollCommand("<C-u>", c -> Scrolling.halfPage(Motions.lastLine(ed) + 1, ed.visibleLineCount(),
                ed.firstVisibleLine(), caretLine(), amount.apply(c), false));
        scrollCommand("<C-e>", c -> Scrolling.lines(Motions.lastLine(ed) + 1, ed.visibleLineCount(),
                ed.firstVisibleLine(), caretLine(), c.count(), true));
        scrollCommand("<C-y>", c -> Scrolling.lines(Motions.lastLine(ed) + 1, ed.visibleLineCount(),
                ed.firstVisibleLine(), caretLine(), c.count(), false));
        zCommand("zz", 'z', false);
        zCommand("z.", 'z', true);
        zCommand("zt", 't', false);
        zCommand("z<CR>", 't', true);
        zCommand("zb", 'b', false);
        zCommand("z-", 'b', true);
        for (char which : new char[] {'H', 'M', 'L'}) {
            motion(String.valueOf(which), MotionType.LINEWISE, c -> Motions.firstNonBlank(c.ed,
                    Scrolling.screenLine(c.ed.lineCount(), c.ed.visibleLineCount(), c.ed.firstVisibleLine(),
                            c.count, which)));
        }
    }

    // ------------------------------------------------------------------ insert commands and .

    /** Mode first: a host may clamp the caret off the line end while still in normal mode. */
    private void enterInsert(int caret) {
        ed.setMode(Mode.INSERT);
        ed.setCaret(caret);
    }

    private void registerInsertCommands() {
        commands.put("i", new CommandSpec(false, c -> enterInsert(ed.caret())));
        commands.put("a", new CommandSpec(false, c -> {
            int caret = ed.caret();
            enterInsert(Math.min(caret + 1, ed.lineEnd(ed.lineOfOffset(caret))));
        }));
        commands.put("I", new CommandSpec(false, c -> {
            int line = caretLine();
            int s = ed.lineStart(line), e = ed.lineEnd(line), i = s;
            while (i < e && (ed.charAt(i) == ' ' || ed.charAt(i) == '\t')) i++;
            enterInsert(i);
        }));
        commands.put("A", new CommandSpec(false, c -> enterInsert(ed.lineEnd(caretLine()))));
        commands.put("o", new CommandSpec(false, c -> {
            int line = caretLine();
            String indent = Operators.leadingWhitespace(ed, line);
            int e = ed.lineEnd(line);
            ed.beginEdit();
            try {
                ed.replace(e, e, "\n" + indent);
            } finally {
                ed.endEdit();
            }
            enterInsert(e + 1 + indent.length());
        }));
        commands.put("O", new CommandSpec(false, c -> {
            int line = caretLine();
            String indent = Operators.leadingWhitespace(ed, line);
            int s = ed.lineStart(line);
            ed.beginEdit();
            try {
                ed.replace(s, s, indent + "\n");
            } finally {
                ed.endEdit();
            }
            enterInsert(s + indent.length());
        }));
        commands.put(".", new CommandSpec(false, c -> {
            if (lastChange == null) {
                ed.beep();
                return;
            }
            Command change = lastChange;
            if (c.hasCount()) {
                change = new Command(c.count(), 0, change.opKey, change.doubled, change.motionKey,
                        change.objectKey, change.cmdKey, change.arg);
            }
            String text = lastInsertedText;
            replaying = true;
            ed.beginEdit();
            try {
                execute(change);
                if (isInsert(ed.mode())) {
                    String all = text;
                    if (change.cmdKey != null && INSERT_COMMANDS.contains(change.cmdKey) && change.count() > 1)
                        all = text + repeatedInsert(change, text, change.count() - 1);
                    int caret = ed.caret();
                    ed.replace(caret, caret, all);
                    ed.setCaret(caret + all.length());
                    ed.setMode(Mode.NORMAL);
                }
            } finally {
                ed.endEdit();
                replaying = false;
            }
            lastChange = change;
            lastInsertedText = text;
        }));
    }

    // ------------------------------------------------------------------ * # g* g#

    private void starSearch(Command c, boolean backward, boolean whole) {
        CharClass cc = charClass();
        int caret = ed.caret();
        int line = caretLine();
        int ls = ed.lineStart(line), le = ed.lineEnd(line);
        int i = caret;
        if (i < le && cc.of(ed.charAt(i)) == CharClass.WORD) {
            while (i > ls && cc.of(ed.charAt(i - 1)) == CharClass.WORD) i--;
        } else {
            while (i < le && cc.of(ed.charAt(i)) != CharClass.WORD) i++;
            if (i >= le) {
                ed.beep();
                return;
            }
        }
        int e = i;
        while (e < le && cc.of(ed.charAt(e)) == CharClass.WORD) e++;
        String quoted = java.util.regex.Pattern.quote(ed.text(i, e));
        String regex = whole ? "\\b" + quoted + "\\b" : quoted;
        ed.setCaret(i);
        for (int n = 0; n < c.count(); n++) ed.search(regex, backward);
        desiredCol = Motions.column(ed, ed.caret());
    }

    private void registerStarSearch() {
        commands.put("*", new CommandSpec(false, c -> starSearch(c, false, true)));
        commands.put("#", new CommandSpec(false, c -> starSearch(c, true, true)));
        commands.put("g*", new CommandSpec(false, c -> starSearch(c, false, false)));
        commands.put("g#", new CommandSpec(false, c -> starSearch(c, true, false)));
    }
}
