# Motion Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Vimulator's one-action-per-key-combination design with a Vim-style command engine (counts × operators × motions/text objects) and add the missing Neovim keybinds, excluding registers and macros.

**Architecture:** A new pure-Java package `vimulator.core` (no jEdit imports) holds a key-token parser and executor (`Engine`), motions, text objects, operators, and ex helpers. It works against an `Editor` interface. Unit tests run that interface against an in-memory `FakeEditor`. In jEdit, `JEditEditor` adapts it to `View`/`JEditTextArea`. `BindingInputHandler` feeds normal-mode and visual-mode keys to the engine first. Any sequence the engine rejects is replayed through the existing props-driven binding table, so `:`, `/`, `u`, `p`, `C-w`, folds and the other existing commands keep working unchanged.

**Tech Stack:** Java (`--release 17`), jEdit 5.6/5.7 API, BeanShell actions (`actions.xml`), `.props` keymaps. No third-party libraries; tests use a small in-repo runner.

**Spec:** No separate spec document. The scope was agreed in conversation on 2026-11-18 and is reproduced in **Scope** below.

## Scope

In: `C-d C-u C-e C-y`, `zz zt zb z<CR> z. z-`, `H M L`; all text objects (`iw aw iW aW i" a" i' a' i` a` i( a( ib i[ a[ i{ a{ iB i< a< it at ip ap`); operator+motion for `d c y > < = g~ gu gU` with counts on both sides; `W B E ge gE`; `.`; `~`; `C-a C-x`; `J gJ`; `r`; `* # g* g#`; visual `o gv J ~ u U` and text objects; insert-mode `C-w C-u C-o`; ex `:s`, `:%s`, ranges, `:noh`, `:x`, and fixing the broken `:wq! :wqa :wqa! :qa!`.

Out: named/clipboard registers (`"x`), macros (`q @`), `d/pattern` and `dn`, `.` after a visual-mode operator, `.` for `p`/`P`, hex/binary `C-a`.

## Global Constraints

- Compile with `--release 17`. System jEdit 5.7 runs on JRE 17; Isabelle bundles JDK 25.
- `src/vimulator/core/**` must not import `org.gjt.*`. `scripts/test.sh` compiles core without `jedit.jar` on the classpath, so a violation fails the build. `java.awt.event.KeyEvent` is allowed only in `KeyTokens`.
- No new third-party dependencies.
- The unnamed register stays `Registers.setRegister('y', …)`. Linewise yanks always end in `"\n"`, because existing `VimulatorUtilities.paste` decides linewise vs. charwise by the trailing newline.
- Columns are character columns (a tab counts as 1). Scrolling uses physical lines and ignores folds.
- Every engine command that changes text runs inside one `beginEdit()`/`endEdit()` pair, so one `u` undoes it.
- Keys not handled by the engine keep working through `Vimulator_keys.props` exactly as before.
- Tooling: `javac` and `ant` are not on PATH on the dev machine. Run the scripts with `JAVA_HOME=/usr/lib/jvm/java-27-openjdk`. `JEDIT_JAR` defaults to `/usr/share/java/jedit/jedit.jar`.

## Review Focus

1. **Empty buffer and empty lines.** Every motion, operator and text object on `"|"` and on `"a\n|\nb"` must beep or no-op, never throw. Pinned by `testNothingThrowsOnEmptyInput` (Task 6) and `testTextObjectsOnEmptyInput` (Task 7).
2. **Last line of the buffer, with and without a trailing newline.** `dd`, `dj`, `dG` on the final line of `"a\nb"` and `"a\nb\n"`. Pinned by `testLastLineEdges` (Task 6).
3. **Invalid key after an operator** (`dz`, `d<C-w>`). After an operator, the sequence cancels with one beep and returns `DONE`. It is never `REJECTED`, so half a sequence can't leak into legacy bindings. A bare `g`/`z` prefix with an unknown second key *is* `REJECTED`, for legacy replay. Pinned by `testInvalidAfterOperatorCancels` (Task 6) and `testUnknownPrefixRejected` (Task 2).
4. **Huge counts** (`99999999999j`). The count saturates at 999999 with no overflow and no exception. Pinned by `testHugeCount` (Task 2).
5. **Unbalanced brackets and unclosed quotes** (`"f(a|"`, `"\"ab|"`). The text object beeps and leaves text unchanged. Pinned by `testUnbalanced` (Task 7).

---

## File Structure

```
scripts/build.sh                       compile src + jar → install/Vimulator.jar (no ant needed)
scripts/test.sh                        compile src/vimulator/core + test, run TestRunner
build.xml                              release=17, exclude test/** and scripts/**
src/vimulator/core/
  TextModel.java                       read-only line/offset view of text
  CharClass.java                       blank / punctuation / keyword classification
  Mode.java, Operator.java, MotionType.java, Range.java
  Editor.java                          host interface the engine drives
  Motions.java                         pure motion functions
  TextObjects.java                     pure text-object functions
  Operators.java                       applies an Operator to a Range on an Editor
  Transforms.java                      ~, J/gJ, C-a/C-x helpers
  Scrolling.java                       C-d/C-u/C-e/C-y/z*/H/M/L arithmetic
  InsertEdits.java                     insert-mode C-w / C-u range computation
  Engine.java                          token parser + dispatcher + state (counts, find, marks, curswant, visual, last change)
  KeyTokens.java                       java.awt KeyEvent → token string
  ex/VimRegex.java, ex/ExRange.java, ex/SubstituteCommand.java, ex/Substitute.java
src/vimulator/JEditEditor.java         Editor implementation over a jEdit View
src/vimulator/inputhandler/BindingInputHandler.java   engine-first routing + replay
src/vimulator/inputhandler/VimulatorInputHandler.java owns one Engine per view
test/vimulator/TestRunner.java         reflective runner: public static void test*() in listed classes
test/vimulator/core/FakeEditor.java    StringBuilder-backed Editor
test/vimulator/core/Fixture.java       "foo |bar" notation, key driving, assertions
test/vimulator/core/*Test.java, test/vimulator/KeymapConsistencyTest.java
```

## Shared Test Interfaces (defined in Task 1, used by every task)

```java
// test/vimulator/core/Fixture.java
static Fixture of(String textWithCaret)   // exactly one '|' marks the caret offset; mode NORMAL; viewport(0,10)
static Fixture lines(int n)               // "l0\nl1\n…\nl{n-1}", caret at 0, viewport(0,10)
Fixture viewport(int firstLine, int height)
Fixture caretLine(int line)               // caret to start of that line
Fixture keys(String keys)                 // tokens: "<Name>" or a single char; "<lt>" is a literal '<'
FakeEditor editor()
String render()                           // text with '|' inserted before the caret char
int beeps(); Mode mode(); String register(); boolean registerLinewise();
String selectedText(); int firstVisibleLine(); int caretLine(); Engine.Result lastResult();
Operator lastBlockOperator(); int[] lastReindent(); String lastSearch();
static void check(String before, String keys, String after)        // also asserts no exception
static void checkRegister(String before, String keys, String after, String reg, boolean linewise)
static void checkRejected(String before, String keys)              // last feed REJECTED, text unchanged
static void checkBeep(String before, String keys)                  // text+caret unchanged, beeps() >= 1
static void eq(Object expected, Object actual); static void assertTrue(String msg, boolean cond)
```

How `Fixture.keys` routes each token:
- In INSERT or REPLACE mode, the fixture handles the key itself. A printable char is inserted at the caret, `<BS>` deletes the char before the caret, and `<Esc>` calls `engine.onInsertExit()` and then `editor.setMode(NORMAL)`.
- In NORMAL or VISUAL modes, the token goes to `engine.feed`.
- On `REJECTED`, the fixture simulates only two legacy bindings: `<Esc>` in a visual mode sets the mode to NORMAL, and `<Esc>` in NORMAL is a no-op. Any other rejection throws `AssertionError("rejected: …")`, except inside `checkRejected`.

`FakeEditor` behaviour that tests depend on:
- `setMode(NORMAL)` from INSERT or REPLACE moves the caret left by one, but not past the line start.
- `setCaret` scrolls the viewport minimally to keep the caret line visible.
- `shiftLines` adds or removes up to 4 leading spaces per line.
- `reindentLines` and `blockOperator` only record their arguments.
- `search` runs a real `java.util.regex` search with wrap-around, starting after the caret (or before it, when searching backward), moves the caret to the match, and records the regex.
- `extraWordChars()` returns `""`.

---

### Task 1: Build/test scaffolding and core interfaces

**Files:**
- Create: `scripts/build.sh`, `scripts/test.sh`, `src/vimulator/core/{TextModel,CharClass,Mode,Operator,Editor,MotionType,Range}.java`, `test/vimulator/TestRunner.java`, `test/vimulator/core/{FakeEditor,Fixture,TextModelTest}.java`
- Modify: `build.xml`. Set javac `release="17"` and `excludes="bin/**,test/**,scripts/**"`, and change the `jedit.install.dir` default to `/usr/share/java/jedit`.

**Interfaces:**
- Produces:
  - `interface TextModel { int length(); char charAt(int offset); int lineCount(); int lineOfOffset(int offset); int lineStart(int line); int lineEnd(int line); }`. `lineEnd` is the offset of the line's terminating `'\n'`, or `length()` for the last line. `lineOfOffset(length())` is the last line.
  - `final class CharClass { CharClass(String extraWordChars); int of(char c); int ofBig(char c); static final int BLANK=0, PUNCT=1, WORD=2; }`.
    - WORD means `Character.isLetterOrDigit(c) || c=='_' || extra.indexOf(c)>=0`. BLANK means `' '`, `'\t'` or `'\n'`. Anything else is PUNCT.
    - `ofBig` returns BLANK or WORD.
  - `enum Mode { NORMAL, VISUAL, VISUAL_LINE, VISUAL_BLOCK, INSERT, REPLACE; boolean isVisual(); }`
  - `enum Operator { DELETE, CHANGE, YANK, SHIFT_RIGHT, SHIFT_LEFT, REINDENT, TOGGLE_CASE, LOWERCASE, UPPERCASE }`
  - `enum MotionType { EXCLUSIVE, INCLUSIVE, LINEWISE }`
  - `record Range(int start, int end, MotionType type)`. These are offsets with `start <= end`. For INCLUSIVE, `end` is the last included char; for LINEWISE, only the lines of `start` and `end` matter.
  - `interface Editor extends TextModel` with exactly: `int caret(); void setCaret(int offset); Mode mode(); void setMode(Mode m); void setSelection(int anchor, int caret, Mode visualMode); String text(int start, int end); void replace(int start, int end, String text); void yank(String text, boolean linewise); void shiftLines(int firstLine, int lastLine, int direction); void reindentLines(int firstLine, int lastLine); void blockOperator(Operator op); void search(String javaRegex, boolean backward); void beginEdit(); void endEdit(); void beep(); String extraWordChars(); int firstVisibleLine(); int visibleLineCount(); void setFirstVisibleLine(int line);`
  - The Fixture/FakeEditor API listed in **Shared Test Interfaces**. `Fixture.of`, `editor` and `render` work now; key driving is wired in Task 2.
  - `TestRunner.main` iterates a `Class<?>[] TESTS` array and invokes every `public static void test*()`. It prints `PASS`/`FAIL name: message` per test and a summary line `N passed, M failed`, and exits 1 on any failure. Each later task appends its test class to `TESTS`.
  - `scripts/test.sh`:
    ```bash
    J="${JAVA_HOME:+$JAVA_HOME/bin/}"
    rm -rf build/test && "${J}javac" --release 17 -d build/test $(find src/vimulator/core test -name '*.java') \
      && "${J}java" -Djava.awt.headless=true -cp build/test vimulator.TestRunner
    ```
  - `scripts/build.sh` compiles `src/**/*.java` against `${JEDIT_JAR:-/usr/share/java/jedit/jedit.jar}` into `build/classes`, then runs `jar cf install/Vimulator.jar -C build/classes . actions.xml *.props`.

- [ ] **Step 1: Write the failing test** `TextModelTest`:

```java
public static void testLines() {
    FakeEditor e = Fixture.of("ab\n|\ncd").editor();
    eq(3, e.lineCount()); eq(0, e.lineStart(0)); eq(2, e.lineEnd(0));
    eq(3, e.lineStart(1)); eq(3, e.lineEnd(1)); eq(6, e.lineEnd(2));
    eq(1, e.lineOfOffset(3)); eq(2, e.lineOfOffset(6)); eq(3, e.caret());
}
public static void testRenderRoundTrip() { eq("ab\n|\ncd", Fixture.of("ab\n|\ncd").render()); }
public static void testCharClass() {
    CharClass c = new CharClass("");
    eq(CharClass.WORD, c.of('_')); eq(CharClass.PUNCT, c.of('.')); eq(CharClass.BLANK, c.of('\t'));
    eq(CharClass.WORD, new CharClass(".").of('.')); eq(CharClass.WORD, c.ofBig('.'));
}
public static void testEmptyBuffer() { FakeEditor e = Fixture.of("|").editor(); eq(1, e.lineCount()); eq(0, e.lineEnd(0)); }
```

- [ ] **Step 2: Run `JAVA_HOME=/usr/lib/jvm/java-27-openjdk scripts/test.sh`.** Expected: a compile failure, because the classes don't exist yet.
- [ ] **Step 3: Implement the files listed above.** Back FakeEditor with a `StringBuilder`, and answer line queries by scanning for `'\n'`.
- [ ] **Step 4: Run `scripts/test.sh`.** Expected: `4 passed, 0 failed`. Then run `JAVA_HOME=… scripts/build.sh`. Expected: `install/Vimulator.jar` is produced.
- [ ] **Step 5: Commit** `git add scripts build.xml src/vimulator/core test && git commit -m "build: add core package, fake editor and test runner"`

---

### Task 2: Engine skeleton with counts and basic motions

**Files:**
- Create: `src/vimulator/core/Engine.java`, `src/vimulator/core/Motions.java`, `test/vimulator/core/BasicMotionTest.java`
- Modify: `test/vimulator/core/Fixture.java` (wire `keys`), `test/vimulator/TestRunner.java`

**Interfaces:**
- Consumes: Task 1 types.
- Produces:
  - `final class Engine { enum Result { PENDING, DONE, REJECTED } Engine(Editor ed); Result feed(String token); boolean isIdle(); void onInsertExit(); int[] lastVisualLines(); }`. `onInsertExit` and `lastVisualLines` are no-op stubs here; `lastVisualLines` returns null until Task 9.
  - `Motions` static functions, all of the form `static int name(TextModel t, CharClass cc, int caret, int count)`. Each returns a target offset, or `-1` on failure (beep). Vertical motions take an extra `int desiredCol` argument.
  - The engine's motion table is `Map<String, MotionSpec>`, where `record MotionSpec(MotionFn fn, MotionType type, boolean keepsColumn)`. Later tasks add rows to it.
  - Engine state used by later tasks: `int desiredCol`, where `Integer.MAX_VALUE` means "end of line".

Grammar: `[count] (motion | operator [count] (motion | textobject | operator-again) | command)`.
- Counts: digits, but `0` only continues a count that's already in progress. A count saturates at 999999.
- The engine returns `REJECTED` (no side effects) when the first non-count key isn't engine-owned, or when a `g`/`z` prefix is followed by a key the engine doesn't own. The caller then replays every key since the last `DONE`, digits included.
- A failed motion beeps and returns `DONE`.
- In NORMAL mode, after every `DONE` the caret is clamped to the line's last char (or the line start, if the line is empty).
- Horizontal motions set `desiredCol` to the new column.

| keys | target | type |
|---|---|---|
| `h <Left> <BS> <C-h>` | count chars left, not past line start; fails at col 0 | EXCLUSIVE |
| `l <Right> <Space>` | count chars right, not past last char; fails at last char | EXCLUSIVE |
| `j <Down> <C-j> <C-n>` / `k <Up> <C-p>` | count lines, column = min(desiredCol, lastCol); fails if out of range | LINEWISE, keepsColumn |
| `0 <Home>` | line start | EXCLUSIVE |
| `^` | first non-blank | EXCLUSIVE |
| `$ <End>` | last char of the line `count-1` lines down; sets desiredCol = MAX | INCLUSIVE |
| `+ <CR> <C-m>` / `-` | first non-blank, count lines down/up | LINEWISE |
| `_` | first non-blank, `count-1` lines down | LINEWISE |
| `gg` / `G` | line `count` (default first/last), clamped, first non-blank | LINEWISE |

- [ ] **Step 1: Write failing tests** `BasicMotionTest`:

```java
public static void testHL() { check("a|bc", "l", "ab|c"); check("a|bc", "5l", "ab|c"); checkBeep("ab|c", "l"); check("ab|c", "2h", "|abc"); checkBeep("|abc", "h"); }
public static void testJKDesiredColumn() { check("ab|cdef\nx\nabcdef", "jj", "abcdef\nx\nab|cdef"); check("|ab\nabcdef", "$j", "ab\nabcde|f"); checkBeep("|a", "j"); }
public static void testLineEnds() { check("  a|bc", "0", "|  abc"); check("  a|bc", "^", "  |abc"); check("|ab\ncd", "2$", "ab\nc|d"); }
public static void testLineMotions() { check("a\n|b\n  c", "+", "a\nb\n  |c"); check("  a\n|b", "-", "  |a\nb"); check("a\nb\n|  c", "gg", "|a\nb\n  c"); check("|a\nb\n  c", "G", "a\nb\n  |c"); check("|a\nb\nc", "2G", "a\n|b\nc"); check("|a\nb\nc", "2gg", "a\n|b\nc"); }
public static void testZeroIsMotionUnlessCounting() { check("ab|c\n" + "x\n".repeat(12), "10j", "abc\n" + "x\n".repeat(9) + "|x\n" + "x\n".repeat(2)); check("ab|c", "0", "|abc"); }
public static void testHugeCount() { checkBeep("|a\nb", "99999999999j"); }
public static void testUnknownPrefixRejected() { checkRejected("|a", "p"); checkRejected("|a", "3p"); checkRejected("|a", "zf"); checkRejected("|a", "<C-w>"); }
```
- [ ] **Step 2: Run `scripts/test.sh`.** Expected: FAIL, because `Engine` doesn't exist.
- [ ] **Step 3: Implement `Engine` and `Motions` for the table above.**
- [ ] **Step 4: Run `scripts/test.sh`.** Expected: all pass.
- [ ] **Step 5: Commit** `feat(core): engine skeleton with counts and basic motions`

---

### Task 3: jEdit integration (engine-first routing with legacy replay)

**Files:**
- Create: `src/vimulator/JEditEditor.java`, `src/vimulator/core/KeyTokens.java`, `test/vimulator/core/KeyTokensTest.java`
- Modify: `src/vimulator/inputhandler/BindingInputHandler.java`, `src/vimulator/inputhandler/VimulatorInputHandler.java`, `src/vimulator/VimulatorUtilities.java:55` (`setMode`)

**Interfaces:**
- Consumes: `Engine`, `Editor`, `Mode`.
- Produces:
  - `KeyTokens.of(KeyEvent e) -> String`, or null when the event carries no token:
    - KEY_TYPED with no CTRL/ALT/META held and a char in `0x21–0x7e` or `>= 0xa0` gives `String.valueOf(ch)`, case preserved.
    - KEY_PRESSED gives: CTRL+letter → `"<C-x>"` (lowercase letter); CTRL+`[` → `"<Esc>"`; `<Esc> <CR> <BS> <Space> <Tab> <Del> <Left> <Right> <Up> <Down> <Home> <End>` from the matching VK codes with no modifiers.
    - Everything else returns null. That includes PAGE_UP/DOWN, CTRL+HOME/END, and modifier-only presses.
  - `VimulatorInputHandler.getEngine() -> Engine`: one per view, created in the `(View, chain)` constructor and passed to its command and visual `BindingInputHandler`s. It stays `null` for the root handler, whose view is null.
  - `JEditEditor(View view)` implements `Editor`, resolving `view.getTextArea()` on every call:
    - `setCaret` → `moveCaretPosition`; `replace` → buffer `remove` + `insert`; `yank` → `Registers.setRegister('y', text)`; `beginEdit`/`endEdit` → buffer compound edit; `beep` → `VimulatorUtilities.beep`.
    - `mode`/`setMode` map to `VimulatorConstants` via `VimulatorUtilities.getMode`/`setMode`. `extraWordChars` → buffer `noWordSep`, or `""` if null.
    - `setSelection`: charwise is `Selection.Range(min, max+1)`; linewise is `Selection.Range(lineStart(minLine), min(lineEnd(maxLine)+1, length))`; block is `Selection.Rect(min, max+1)`.
    - Viewport: `firstVisibleLine` → `getFirstPhysicalLine`; `visibleLineCount` → `getVisibleLines`; `setFirstVisibleLine` → `setFirstPhysicalLine`.
    - `shiftLines` selects the lines and calls `shiftIndentRight`/`shiftIndentLeft`; `reindentLines` → `buffer.indentLines(first, last)`.
    - `search` and `blockOperator` beep for now; Task 12 and Task 9 implement them.
  - `VimulatorUtilities.setMode` calls `getEngine().onInsertExit()` when leaving an INSERT-bit mode, *before* moving the caret left.

Routing algorithm for `BindingInputHandler.processKeyEvent`. This applies only to the command and visual handlers; `InsertInputHandler` is untouched.
```
token = KeyTokens.of(evt)
if engine == null or token == null:
    if engine == null or engine.isIdle() → legacy(evt) else evt.consume(); return
pending.add(evt)                          // keep KeyEvent objects for replay
switch engine.feed(token):
  PENDING  → consume
  DONE     → pending.clear(); consume
  REJECTED → copy = pending; pending.clear(); for e in copy: legacy(e)
// legacy(e) = the existing commandKeyPressed / commandKeyTyped dispatch
```

- [ ] **Step 1: Write failing tests** in `KeyTokensTest`. Build events with `new KeyEvent(new java.awt.Canvas(), id, 0, mods, code, ch)`:

```java
public static void testTyped() { eq("d", typed('d', 0)); eq("D", typed('D', SHIFT_DOWN_MASK)); eq("$", typed('$', SHIFT_DOWN_MASK)); eq(null, typed('d', CTRL_DOWN_MASK)); eq(null, typed(' ', 0)); }
public static void testPressed() { eq("<C-d>", pressed(VK_D, CTRL_DOWN_MASK)); eq("<Esc>", pressed(VK_OPEN_BRACKET, CTRL_DOWN_MASK)); eq("<Esc>", pressed(VK_ESCAPE, 0)); eq("<CR>", pressed(VK_ENTER, 0)); eq("<Space>", pressed(VK_SPACE, 0)); eq(null, pressed(VK_PAGE_DOWN, 0)); eq(null, pressed(VK_SHIFT, SHIFT_DOWN_MASK)); eq(null, pressed(VK_D, 0)); }
```
- [ ] **Step 2: Run `scripts/test.sh`.** Expected: FAIL.
- [ ] **Step 3: Implement `KeyTokens`, `JEditEditor`, engine ownership, and the routing above.**
- [ ] **Step 4: Run `scripts/test.sh` (all pass) and `scripts/build.sh` (jar built).** Then install `install/Vimulator.jar` into your jEdit jars directory, restart jEdit, and check by hand:
  - `5j`, `$`, `gg`, `3G` and `0` move the caret.
  - These legacy bindings still work through replay: `:w`, `/foo` + `n`, `u`, `C-r`, `p`, `3p`, `zf`, `C-w v`.
  - `i` + typing + `Esc` still works.
- [ ] **Step 5: Commit** `feat: route command/visual keys through the engine with legacy replay`

---

### Task 4: Word and paragraph motions

**Files:** Modify `Motions.java` and `Engine.java` (table rows). Test: `test/vimulator/core/WordMotionTest.java`.

**Interfaces:** Produces the motion rows below. `w`, `W` and `}` may return `t.length()`, one past the last char, to mean "end of buffer". The engine clamps that for cursor moves, and operators use it as-is (Task 6).

| keys | semantics (Vim) | type |
|---|---|---|
| `w`/`W` | start of the next word/WORD; an empty line counts as a word; moving past the last word gives `length()`; already at the last char fails | EXCLUSIVE |
| `b`/`B` | start of the current or previous word; an empty line is a stop; fails at offset 0 | EXCLUSIVE |
| `e`/`E` | end of the next word, skipping blanks and empty lines; fails at the last char | INCLUSIVE |
| `ge`/`gE` | end of the previous word; an empty line is a stop; fails at offset 0 | INCLUSIVE |
| `}`/`{` | next/previous empty (length-0) line; if there is none, `length()` / `0` | EXCLUSIVE |

- [ ] **Step 1: Write failing tests:**

```java
public static void testW() { check("|foo.bar baz", "w", "foo|.bar baz"); check("|foo.bar baz", "3w", "foo.bar |baz"); check("|foo\n\nbar", "w", "foo\n|\nbar"); check("|foo\n\nbar", "2w", "foo\n\n|bar"); check("foo |bar", "w", "foo ba|r"); checkBeep("foo ba|r", "w"); }
public static void testBigW() { check("|foo.bar baz", "W", "foo.bar |baz"); check("a foo.ba|r b", "B", "a |foo.bar b"); check("|a foo.bar b", "E", "a foo.ba|r b"); }
public static void testB() { check("foo ba|r", "b", "foo |bar"); check("foo |bar", "b", "|foo bar"); check("foo\n\n|bar", "b", "foo\n|\nbar"); checkBeep("|foo", "b"); }
public static void testE() { check("|foo bar", "e", "fo|o bar"); check("fo|o bar", "e", "foo ba|r"); check("fo|o\n\n  bar", "e", "foo\n\n  ba|r"); }
public static void testGe() { check("foo ba|r", "ge", "fo|o bar"); check("foo\n\n|bar", "ge", "foo\n|\nbar"); check("a.b c|d", "gE", "a.|b cd"); checkBeep("|foo", "ge"); }
public static void testParagraph() { check("|a\nb\n\nc", "}", "a\nb\n|\nc"); check("a\n\nb\n|c", "{", "a\n|\nb\nc"); check("|a\nb", "}", "a\n|b"); check("a\n|b", "{", "|a\nb"); }
```
- [ ] **Steps 2–4:** Run (FAIL), implement, then run (PASS). Then check `w b e W B E ge { }` by hand in jEdit.
- [ ] **Step 5: Commit** `feat(core): word, WORD and paragraph motions`

---

### Task 5: Find-char, marks and bracket matching as motions

**Files:** Modify `Motions.java`, `Engine.java`. Test: `test/vimulator/core/FindMotionTest.java`. The legacy `VimulatorPlugin.lastFindChar` and `VimulatorUtilities.markLines/markColumns` become unreachable now; Task 15 deletes them.

**Interfaces:**
- Produces:
  - Char-argument motions. The next token is the argument; `<Space>` means `' '` and `<Tab>` means `'\t'`, while `<Esc>` cancels with `DONE` and no beep.
    - `f` INCLUSIVE, `t` INCLUSIVE, `F` EXCLUSIVE, `T` EXCLUSIVE. All are limited to the current line, use the count-th occurrence, and fail if there aren't enough.
    - `;` repeats the last find, `,` repeats it reversed. When repeating `t`/`T`, a char adjacent to the caret is skipped (Vim's cpo-`;` behaviour).
    - Engine state: `record LastFind(char ch, boolean backward, boolean till)`.
  - `%`:
    - Without a count: if the caret isn't on one of `()[]{}`, use the first such char after it on the line, then jump to its match by nesting across lines. INCLUSIVE. Fails if there's no bracket or no match.
    - `N%` goes to line `(N * lineCount + 99) / 100` (1-based), first non-blank. LINEWISE.
  - Marks:
    - `m{a-z}` stores (line, col) in the engine. It's a command, not a motion, and returns DONE.
    - `` `{a-z} `` goes to the exact position, clamped. EXCLUSIVE.
    - `'{a-z}` goes to the first non-blank of the mark's line. LINEWISE.
    - An unset mark fails.

- [ ] **Step 1: Write failing tests:**

```java
public static void testF() { check("|a,b,c", "f,", "a|,b,c"); check("|a,b,c", "2f,", "a,b|,c"); checkBeep("|a,b,c", "3f,"); check("a,b,|c", "F,", "a,b|,c"); check("|a,b,c", "2t,", "a,|b,c"); }
public static void testRepeat() { check("|a,b,c,d", "f,;", "a,b|,c,d"); check("|a,b,c,d", "f,;,", "a|,b,c,d"); check("|ab,c,d", "t,;", "ab,|c,d"); }
public static void testPercent() { check("|(a [b] c)", "%", "(a [b] c|)"); check("x |a (b)", "%", "x a (b|)"); check("{\n|}", "%", "|{\n}"); checkBeep("|abc", "%"); check("|" + "x\n".repeat(9) + "x", "50%", "x\n".repeat(4) + "|x\n" + "x\n".repeat(4) + "x"); }
public static void testMarks() { check("ab|c\nd", "majmb`a", "ab|c\nd"); check("  ab|c\nd", "maj'a", "  |abc\nd"); checkBeep("|abc", "`q"); }
```
- [ ] **Steps 2–4:** Run (FAIL), implement, then run (PASS). Then check `f t F T ; , %` and marks by hand in jEdit.
- [ ] **Step 5: Commit** `feat(core): f/t/F/T, ; , %, and marks as motions`

---

### Task 6: Operators d c y with motions, doubled forms and aliases

**Files:** Create `src/vimulator/core/Operators.java`. Modify `Engine.java`. Test: `test/vimulator/core/OperatorTest.java`.

**Interfaces:**
- Consumes: the motion table (Tasks 2, 4, 5); `Range`; `Editor.yank/replace`.
- Produces:
  - `Operators.apply(Editor ed, Operator op, Range r, CharClass cc) -> boolean` (false means beep). Task 8 adds the other operators to this same entry point.
  - The engine records the last executed change as `record Change(Operator op, String motionKey, Character charArg, int count, String insertedText)`; `op` is null for non-operator commands, with `motionKey` holding the command key. Task 11 replays these records.
  - Operator keys: `d`, `c`, `y`. Doubling an operator (`dd`, `cc`, `yy`) acts linewise on `count` lines starting at the caret line, clamped to the end of the buffer.
  - The operator count and the motion count multiply.

Range rules (Vim `:h exclusive`, `:h cw`, `:h dw`):
1. Motion from the caret to a target. If the target is before the caret, swap them. INCLUSIVE adds the end char.
2. **Exclusive at col 0:** if an EXCLUSIVE range ends at column 0 of a later line, the end moves to the end of the previous line and becomes INCLUSIVE. If the start is also at or before the first non-blank of its line, the whole range becomes LINEWISE.
3. **`cw`/`cW`** with the caret on a non-blank behaves as `ce`/`cE`.
4. **`dw`/`cw`/`yw`**: if the last word moved over ends a line, the range stops at that end of line instead of continuing into the next line.
5. LINEWISE span: from `lineStart(first)` to `min(lineEnd(last)+1, length)`. When `last` is the final line and `first > 0`, the span is from `lineEnd(first-1)` to `length` instead. The register gets the lines joined with `"\n"` plus a trailing `"\n"`, with linewise = true.

Caret after each operator:
- Charwise delete: the range start.
- Linewise delete: first non-blank of the line now at the `first` index, clamped to the last line.
- Yank: the range start, but a linewise yank keeps the caret where it is unless the range starts above the caret line.
- Change: enters INSERT. A charwise change leaves the caret at the range start. A linewise change replaces the lines with one line holding the first line's leading whitespace, and the caret goes to its end.

Aliases are parsed as their expansion:
| key | expands to |
|---|---|
| `x` | `dl` (clamped to the line) |
| `X` | `dh` |
| `D` | `d$` |
| `C` | `c$` |
| `s` | `cl` |
| `S` | `cc` |
| `Y` | `yy` |

An invalid key after an operator cancels with one beep and returns `DONE` (Review Focus 3).

- [ ] **Step 1: Write failing tests:**

```java
public static void testDw() { checkRegister("foo |bar baz", "dw", "foo |baz", "bar ", false); check("foo |bar\nbaz", "dw", "foo| \nbaz"); check("foo |bar baz", "d2w", "foo| "); check("|a b c d e f g", "2d3w", "|g"); }
public static void testCwIsCe() { check("foo |bar baz", "cw", "foo | baz"); eq(Mode.INSERT, of("foo |bar baz").keys("cw").mode()); check("foo |bar baz", "de", "foo | baz"); }
public static void testExclusiveRule() { check("one |two\n\nthree", "d}", "one| \n\nthree"); check("|one two\n\nthree", "d}", "|\nthree"); }
public static void testLinewise() { checkRegister("|foo\nbar", "dd", "|bar", "foo\n", true); check("foo\n|bar", "dd", "|foo"); check("  a\n|b\n  c", "dd", "  a\n  |c"); check("|a\nb\nc", "5dd", "|"); check("a\n|b\nc", "dk", "|c"); check("|a\nb\nc", "dj", "|c"); checkBeep("|a\nb", "dk"); checkBeep("a\n|b", "dj"); check("a\n|b\nc", "dG", "|a"); check("a\n|b\nc", "dgg", "|c"); }
public static void testFindOps() { check("foo(|bar)", "dt)", "foo(|)"); check("foo(|bar)", "df)", "foo|("); }
public static void testChangeAndYank() { check("  |foo", "cc", "  |"); check("a\n|  b\nc\nd", "cj", "a\n  |\nd"); checkRegister("foo\n|bar\nbaz", "yy", "foo\n|bar\nbaz", "bar\n", true); checkRegister("foo |bar", "yb", "|foo bar", "foo ", false); }
public static void testAliases() { check("ab|c", "x", "a|b"); check("a|bc", "5x", "|a"); checkBeep("|abc", "X"); check("ab|c", "X", "a|c"); check("a|bc def", "D", "|a"); check("a|bc", "C", "a|"); check("a|bc", "s", "a|c"); check("  |ab", "S", "  |"); checkRegister("|ab", "Y", "|ab", "ab\n", true); }
public static void testInvalidAfterOperatorCancels() { Fixture f = of("|abc").keys("dz"); eq("|abc", f.render()); eq(1, f.beeps()); eq(Engine.Result.DONE, f.lastResult()); eq("|bc", f.keys("x").render()); }
public static void testLastLineEdges() { check("a\n|b", "dd", "|a"); check("a\nb\n|", "dd", "a\n|b"); checkBeep("a\n|b", "dj"); check("a\n|b\n", "dG", "|a"); }
public static void testNothingThrowsOnEmptyInput() {
    for (String k : List.of("h","l","j","k","w","b","e","ge","W","B","E","0","^","$","{","}","%","G","gg","dd","dw","cw","yy","x","X","D","C","s","S","de","d}","dG"))
        for (String s : List.of("|", "a\n|\nb")) of(s).keys(k);   // must not throw
}
```
- [ ] **Steps 2–4:** Run (FAIL), implement, then run (PASS). Then check by hand in jEdit: `dw cw yw dj dG dt) 3dd 2d3w`, and confirm a single `u` restores each one.
- [ ] **Step 5: Commit** `feat(core): d/c/y operators over motions with Vim range rules`

---

### Task 7: Text objects

**Files:** Create `src/vimulator/core/TextObjects.java`. Modify `Engine.java`. Test: `test/vimulator/core/TextObjectTest.java`.

**Interfaces:**
- Produces: `static Range name(TextModel t, CharClass cc, int caret, int count)`, returning null on failure. The engine accepts `i`/`a` + object key after an operator; Task 9 adds visual mode.

| keys | semantics | type |
|---|---|---|
| `iw`/`iW` | a run of the caret char's class (word, punct or blank). Count N covers N runs | INCLUSIVE |
| `aw`/`aW` | the word plus trailing blanks, or leading blanks if there are no trailing ones | INCLUSIVE |
| `i"` `i'` `` i` `` / `a"`… | current line only. If the caret is inside a quote pair, use that pair; otherwise use the first pair after the caret. `\` escapes a quote. `a` includes trailing blanks, or leading blanks if none. Fails if there's no pair | INCLUSIVE |
| `i( i) ib`, `i[ i]`, `i{ i} iB`, `i< i>` / `a…` | the innermost pair enclosing the caret (a caret on a bracket counts as inside), or the count-th enclosing pair. Fails if not inside one. For `i`: an empty interior is a no-op with no beep. If the open bracket is the last char on its line and the close bracket is the first non-blank on its line, the interior becomes the lines in between, LINEWISE | INCLUSIVE / LINEWISE |
| `ip`/`ap` | a run of lines that are all blank or all non-blank; `ap` adds the following blank lines, or the preceding ones if none follow | LINEWISE |
| `it`/`at` | the enclosing `<name …>…</name>` pair, nesting-aware; count gives outer pairs; self-closing tags are ignored | INCLUSIVE |

For a linewise `ci{`, the interior lines are replaced with one empty line, and the engine enters INSERT on it.

- [ ] **Step 1: Write failing tests:**

```java
public static void testWord() { check("foo b|ar baz", "diw", "foo | baz"); check("foo b|ar baz", "daw", "foo |baz"); check("foo |bar", "daw", "fo|o"); check("foo  | bar", "diw", "foo|bar"); check("a foo.b|ar b", "diW", "a | b"); check("foo b|ar baz", "ciw", "foo | baz"); }
public static void testQuotes() { check("x = \"he|llo\" y", "di\"", "x = \"|\" y"); check("x = \"he|llo\" y", "da\"", "x = |y"); check("|x = \"hello\"", "di\"", "x = \"|\""); check("\"a\\\"|b\"", "di\"", "\"|\""); check("'a|b'", "di'", "'|'"); check("`a|b`", "di`", "`|`"); checkBeep("|abc", "di\""); }
public static void testBrackets() { check("f(a, |b)", "di(", "f(|)"); check("f(a, |b)", "dib", "f(|)"); check("f(a, |b)", "da(", "|f"); check("f|(a)", "di(", "f(|)"); check("((a|b))", "2di(", "(|)"); check("x[|1]", "di[", "x[|]"); check("<|a>", "di<", "<|>"); check("{|a}", "diB", "{|}"); check("f(|)", "di(", "f(|)"); checkBeep("|a (b)", "di("); }
public static void testMultilineBraces() { check("if {\n  |foo\n}", "di{", "if {\n|}"); Fixture f = of("if {\n  |foo\n}").keys("ci{"); eq("if {\n|\n}", f.render()); eq(Mode.INSERT, f.mode()); }
public static void testParagraph() { check("a\n|b\n\nc", "dip", "|\nc"); check("a\n|b\n\nc", "dap", "|c"); check("a\n\n|b", "dap", "|a"); }
public static void testTags() { check("<a><b>|x</b></a>", "dit", "<a><b>|</b></a>"); check("<a><b>|x</b></a>", "dat", "<a>|</a>"); check("<a><b>|x</b></a>", "2dit", "<a>|</a>"); check("<div class=\"x\">h|i</div>", "dit", "<div class=\"x\">|</div>"); }
public static void testUnbalanced() { checkBeep("f(a|", "di("); checkBeep("\"ab|", "di\""); checkBeep("a)|b(", "di("); }
public static void testTextObjectsOnEmptyInput() { for (String k : List.of("diw","daw","di\"","di(","dip","dap","dit")) for (String s : List.of("|", "a\n|\nb")) of(s).keys(k); }
```
- [ ] **Steps 2–4:** Run (FAIL), implement, then run (PASS). Then check `ca"`, `da[`, `ci(`, `dit` and `yap` by hand in jEdit.
- [ ] **Step 5: Commit** `feat(core): word, quote, bracket, paragraph and tag text objects`

---

### Task 8: Case, indent, join, increment and replace

**Files:** Create `src/vimulator/core/Transforms.java`. Modify `Operators.java`, `Engine.java`. Test: `test/vimulator/core/TransformTest.java`.

**Interfaces:**
- Produces:
  - Operators `g~`, `gu`, `gU`, `>`, `<` and `=` go through `Operators.apply` with any motion or text object. Their doubled forms are linewise on `count` lines: `g~~` or `g~g~`, `guu` or `gugu`, `gUU` or `gUgU`, `>>`, `<<`, `==`.
    - `>` and `<` call `ed.shiftLines(first, last, ±1)` once per range.
    - `=` calls `ed.reindentLines(first, last)`.
    - Caret afterwards: the case operators leave it at the range start; shift and `=` leave it at the first non-blank of the first line.
  - Commands, all recorded as `Change`s for Task 11:
    - `~` toggles `count` chars from the caret (stopping at end of line) and moves the caret past them, clamped.
    - `J` joins `max(count,2)` lines. It strips the leading whitespace of each joined line and inserts one space, except when the joined text starts with `)` or the previous line ends in a blank. The caret goes to the last inserted space if there is one, otherwise to the first char of the last appended text. Fails on the last line.
    - `gJ` joins with no whitespace changes; the caret goes to the first char of the last appended text.
    - `<C-a>`/`<C-x>` find a decimal number at or after the caret on the line (including a `-` directly before the digits), add ±count, and leave the caret on the last digit. Fails if there's no number.
    - `r{c}` replaces `count` chars, failing if fewer remain on the line, and leaves the caret on the last replaced char.
  - `Transforms` exposes `static String toggleCase(String s)` and `static int[] findNumber(TextModel t, int caret)`, which returns `{start, endExclusive}` or null.

- [ ] **Step 1: Write failing tests:**

```java
public static void testCaseOps() { check("|hello world", "g~w", "|HELLO world"); check("|HeLLo", "guu", "|hello"); check("|HeLLo", "gUU", "|HELLO"); check("a |bc d", "gUiw", "a |BC d"); check("|ab", "~", "A|b"); check("|abc", "5~", "AB|C"); }
public static void testShift() { check("|a", ">>", "    |a"); check("|a\nb", ">j", "    |a\n    b"); check("|a\nb\nc", "2>>", "    |a\n    b\nc"); check("      |a", "<<", "  |a"); int[] r = of("|a\nb").keys("=j").lastReindent(); eq(0, r[0]); eq(1, r[1]); }
public static void testJoin() { check("|a\n  b", "J", "a| b"); check("|a\n)b", "J", "a|)b"); check("|a \nb", "J", "a |b"); check("|a\nb\nc", "3J", "a b| c"); check("|a\n  b", "gJ", "a|  b"); checkBeep("|a", "J"); }
public static void testIncrement() { check("x |9 y", "<C-a>", "x 1|0 y"); check("|foo 41", "<C-a>", "foo 4|2"); check("x -|1", "<C-a>", "x |0"); check("|1", "5<C-a>", "|6"); check("|0", "<C-x>", "-|1"); checkBeep("|abc", "<C-a>"); }
public static void testReplace() { check("|abc", "rx", "|xbc"); check("|abc", "2rx", "x|xc"); checkBeep("|ab", "3rx"); }
```
- [ ] **Steps 2–4:** Run (FAIL), implement, then run (PASS). Then check `gUiw`, `>ip`, `==`, `J`, `C-a` and `~` by hand in jEdit. jEdit's own indent width applies to `>`.
- [ ] **Step 5: Commit** `feat(core): case/shift/reindent operators, ~, J/gJ, C-a/C-x, r`

---

### Task 9: Visual modes through the engine

**Files:** Modify `Engine.java` and `JEditEditor.java`. Test: `test/vimulator/core/VisualTest.java`.

`JEditEditor.blockOperator` maps each operator to jEdit as follows:
| operator | jEdit call |
|---|---|
| DELETE | yank `getSelectedText()`, then `setSelectedText("")` |
| YANK | yank `getSelectedText()` |
| SHIFT_RIGHT / SHIFT_LEFT | `shiftIndentRight()` / `shiftIndentLeft()` |
| UPPERCASE / LOWERCASE | `toUpperCase()` / `toLowerCase()` |
| TOGGLE_CASE | replace each selection's text with `Transforms.toggleCase` |

**Interfaces:**
- Consumes: the motion table, the text objects, `Operators.apply`, `Transforms`.
- Produces:
  - `v`, `V` and `<C-v>` enter the mode with anchor = caret. Pressing the same key again exits to NORMAL; pressing a different visual key switches modes and keeps the anchor.
  - `Editor.mode()` is the source of truth. If the mode is visual but the engine has no anchor (because legacy code entered it), the anchor is the caret.
  - Every motion moves the caret, then calls `setSelection(anchor, caret, mode)` and saves `lastVisual = (anchor, caret, mode)`.
  - Operator keys apply to the selection immediately and return to NORMAL:

    | key | operator / effect |
    |---|---|
    | `d` `x` | DELETE |
    | `c` `s` | CHANGE |
    | `y` | YANK |
    | `>` `<` | SHIFT_RIGHT / SHIFT_LEFT |
    | `=` | REINDENT |
    | `~` | TOGGLE_CASE |
    | `u` | LOWERCASE |
    | `U` | UPPERCASE |
    | `J` / `gJ` | join the selected lines |

    - VISUAL uses an INCLUSIVE range from min to max; VISUAL_LINE uses LINEWISE.
    - In VISUAL_BLOCK, only `d x y > < ~ u U` are handled, by calling `ed.blockOperator(op)` and then `setMode(NORMAL)`.
  - `o` and `O` swap the anchor and caret.
  - `gv` (from NORMAL) restores `lastVisual`; it beeps if there's none.
  - Text objects in visual mode replace the selection with the object's range. In VISUAL_LINE, every object except `ip`/`ap` switches the mode to VISUAL.
  - `lastVisualLines()` returns `{minLine, maxLine}` of `lastVisual`, or null; Task 14 uses it.
  - Keys the engine rejects in visual modes, which replay to legacy bindings: `I A p P r <Esc> <C-c> :`, and `c` in VISUAL_BLOCK.

- [ ] **Step 1: Write failing tests:**

```java
public static void testCharwise() { check("|foo bar", "vlld", "| bar"); eq("foo bar", of("|foo bar").keys("vee").selectedText()); check("foo b|ar baz", "viwd", "foo | baz"); checkRegister("|ab", "vly", "|ab", "ab", false); }
public static void testLinewise() { check("|a\nb\nc", "Vjd", "|c"); check("|a\nb", "Vj>", "    |a\n    b"); Fixture f = of("a\n|b\nc").keys("Vc"); eq("a\n|\nc", f.render()); eq(Mode.INSERT, f.mode()); check("|a\nb\nc", "VjJ", "a| b\nc"); }
public static void testCase() { check("|abc", "vl~", "|ABc"); check("|abc", "vlU", "|ABc"); check("|ABC", "vlu", "|abC"); }
public static void testSwapAndReselect() { check("a|bcd", "vllohd", "|"); check("|abc", "vl<Esc>gvd", "|c"); checkBeep("|abc", "gv"); }
public static void testModeSwitch() { eq(Mode.VISUAL_LINE, of("|a").keys("vV").mode()); eq(Mode.NORMAL, of("|a").keys("vv").mode()); eq(Mode.VISUAL, of("|a b").keys("Viw").mode()); }
public static void testBlockDelegates() { Fixture f = of("|ab\ncd").keys("<C-v>jld"); eq(Operator.DELETE, f.lastBlockOperator()); eq(Mode.NORMAL, f.mode()); checkRejected("|ab\ncd", "<C-v>jc"); }
```
- [ ] **Steps 2–4:** Run (FAIL), implement, then run (PASS). Then check by hand in jEdit:
  - `vi(`, `va"y`, `V}>`, `vjo`, `gv`, `C-v jj d`, and `C-v jj I` (legacy multiline insert still works).
  - Visual `p` and `r` still work.
- [ ] **Step 5: Commit** `feat(core): visual, visual-line and visual-block handling in the engine`

---

### Task 10: Scrolling and screen motions

**Files:** Create `src/vimulator/core/Scrolling.java`. Modify `Engine.java`. Test: `test/vimulator/core/ScrollTest.java`.

**Interfaces:**
- Produces engine commands. All of them call `setFirstVisibleLine` *before* `setCaret`, and vertical moves keep `desiredCol`. Below, `V = visibleLineCount()`, `N = lineCount()`, `f = firstVisibleLine()`, `c` = caret line.

| key | first visible line → | caret line → | fails when |
|---|---|---|---|
| `<C-d>` | `min(f+amt, max(0, N-V))` | `min(c+amt, N-1)` | `c == N-1` |
| `<C-u>` | `max(0, f-amt)` | `max(0, c-amt)` | `c == 0` |
| `<C-e>` | `min(f+count, N-1)` | `max(c, f')` | — |
| `<C-y>` | `max(0, f-count)` | `min(c, f'+V-1)` | — |
| `zz` / `z.` | `max(0, c-(V-1)/2)` | unchanged | — |
| `zt` / `z<CR>` | `c` | unchanged | — |
| `zb` / `z-` | `max(0, c-V+1)` | unchanged | — |

- `amt`: the given count, saved as the new scroll amount. Without a count, the saved amount is used, defaulting to `max(1, V/2)`.
- `z<CR>`, `z.` and `z-` also move the caret to the first non-blank. A count on any `z` command first moves the caret to line `count` (1-based).
- Motions, all LINEWISE, landing on the first non-blank, and usable with operators:
  - `H`: line `f + count - 1`, clamped to the last visible line.
  - `L`: line `last - (count-1)`, clamped to `f`, where `last = min(f+V-1, N-1)`.
  - `M`: line `f + (min(V, N-f) - 1) / 2`.

- [ ] **Step 1: Write failing tests:**

```java
public static void testCtrlD() { Fixture f = lines(30).keys("<C-d>"); eq(5, f.firstVisibleLine()); eq(5, f.caretLine()); f = lines(30).keys("2<C-d>"); eq(2, f.caretLine()); f.keys("<C-d>"); eq(4, f.caretLine()); eq(4, f.firstVisibleLine()); f = lines(30).viewport(20,10).caretLine(25).keys("<C-d>"); eq(20, f.firstVisibleLine()); eq(29, f.caretLine()); eq(1, lines(30).viewport(20,10).caretLine(29).keys("<C-d>").beeps()); }
public static void testCtrlU() { Fixture f = lines(30).viewport(10,10).caretLine(15).keys("<C-u>"); eq(5, f.firstVisibleLine()); eq(10, f.caretLine()); eq(1, lines(30).keys("<C-u>").beeps()); }
public static void testCtrlEY() { Fixture f = lines(30).keys("<C-e>"); eq(1, f.firstVisibleLine()); eq(1, f.caretLine()); f = lines(30).caretLine(5).keys("<C-e>"); eq(5, f.caretLine()); f = lines(30).viewport(5,10).caretLine(14).keys("<C-y>"); eq(4, f.firstVisibleLine()); eq(13, f.caretLine()); }
public static void testZ() { eq(11, lines(30).caretLine(15).keys("zz").firstVisibleLine()); eq(15, lines(30).caretLine(15).keys("zt").firstVisibleLine()); eq(6, lines(30).caretLine(15).keys("zb").firstVisibleLine()); check("a\n  b|c", "z<CR>", "a\n  |bc"); check("a\n  b|c", "zt", "a\n  b|c"); }
public static void testHML() { eq(10, lines(30).viewport(10,10).caretLine(15).keys("H").caretLine()); eq(12, lines(30).viewport(10,10).caretLine(15).keys("3H").caretLine()); eq(19, lines(30).viewport(10,10).caretLine(15).keys("L").caretLine()); eq(14, lines(30).viewport(10,10).caretLine(10).keys("M").caretLine()); eq(27, lines(30).viewport(25,10).caretLine(25).keys("M").caretLine()); eq(25, lines(30).viewport(10,10).caretLine(15).keys("dL").editor().lineCount()); }
```
- [ ] **Steps 2–4:** Run (FAIL), implement, then run (PASS). Then check `C-d`, `C-u`, `5C-d`, `C-e`, `C-y`, `zz`, `zt`, `zb`, `H`, `M`, `L` and `dL` by hand in a long jEdit file.
- [ ] **Step 5: Commit** `feat(core): scrolling (C-d/C-u/C-e/C-y/z*) and H/M/L`

---

### Task 11: Insert commands in the engine and `.` repeat

**Files:** Modify `Engine.java`. Test: `test/vimulator/core/RepeatTest.java`.

**Interfaces:**
- Consumes: the `Change` records from Tasks 6–8; `onInsertExit` (wired in Task 3).
- Produces:
  - Engine-owned insert commands `i a I A o O`. The legacy actions behind them stay in place until Task 15.
  - `o`/`O` open the new line with the current line's indentation.
  - Recording an insert:
    - When the engine enters INSERT (via a change, an insert command or `c`), it records `insertStart = caret`.
    - `onInsertExit()` then stores `text(insertStart, caret)` in `lastChange.insertedText` if `caret >= insertStart`, and otherwise an empty string.
    - For insert commands with count N > 1, the captured text is inserted N−1 more times at the caret before the mode changes.
    - `onInsertExit()` does nothing when no recording is active.
  - `.` replays `lastChange` inside one edit group:
    - A new count replaces the stored one; without one, the stored count is used.
    - For changes that entered INSERT, it inserts `insertedText` at the caret and then calls `setMode(NORMAL)` without recording.
    - A replay never overwrites `lastChange`.
    - Yanks, motions and visual operators are not changes. `.` with no change beeps.

- [ ] **Step 1: Write failing tests:**

```java
public static void testDotOperator() { check("|a b c d", "dw.", "|c d"); check("|a b c d", "dw2.", "|d"); check("|abc", "x..", "|"); check("|ab", "dlyl.", "|"); check("|1", "<C-a>.", "|3"); }
public static void testDotChange() { check("|foo foo", "cwbar<Esc>w.", "bar ba|r"); check("|a b", "ciwx<Esc>w.", "x |x"); }
public static void testInsertCommands() { check("|a\nb", "Ax<Esc>j.", "ax\nb|x"); check("|a", "3ix<Esc>", "xx|xa"); check("|a\nb", "ox<Esc>.", "a\nx\n|x\nb"); check("  |a", "Ox<Esc>", "  |x\n  a"); check("  a|b", "Ix<Esc>", "  |xab"); }
public static void testDotNothing() { checkBeep("|a", "."); }
```
- [ ] **Steps 2–4:** Run (FAIL), implement, then run (PASS). Then check by hand in jEdit:
  - `ciwfoo<Esc>` followed by `.` on another word.
  - `dd...`, `A;<Esc>j.`
  - `3ix<Esc>`
- [ ] **Step 5: Commit** `feat(core): engine-owned insert commands and dot repeat`

---

### Task 12: `*`, `#`, `g*`, `g#` and search-direction fix

**Files:** Modify `Engine.java`, `JEditEditor.java` (`search`), `src/vimulator/VimulatorCommandLine.java:74-81,210-231`. Test: `test/vimulator/core/StarSearchTest.java`.

**Interfaces:**
- Produces:
  - `*`/`#` take the keyword under the caret, or the first keyword after the caret on the line (beep if none). The engine moves the caret to the start of that keyword, then calls `ed.search("\\b" + Pattern.quote(word) + "\\b", backward)` count times. `g*`/`g#` use `Pattern.quote(word)` without the `\b`.
  - `VimulatorCommandLine.search(String javaRegex, boolean reverse)` is a new public method. It stores `lastSearchRegex` and `lastSearchReverse` in fields, sets `Registers` `'/'` to the regex, and runs `PatternSearchMatcher` + `SearchAndReplace.find` the same way `processSearch` does.
  - `processSearch` also stores both fields. `repeatSearch(boolean opposite)` uses `lastSearchRegex` and `lastSearchReverse ^ opposite` directly instead of re-parsing through `prefix`, which fixes `n` after `?` and after `#`.
  - `JEditEditor.search` → `VimulatorPlugin.getCommandLine(view).search(regex, backward)`.

- [ ] **Step 1: Write failing tests:**

```java
public static void testStar() { Fixture f = of("|foo bar foo").keys("*"); eq("foo bar |foo", f.render()); eq("\\b\\Qfoo\\E\\b", f.lastSearch()); check("foo bar |foo", "#", "|foo bar foo"); check("|  foo bar foo", "*", "  foo bar |foo"); eq("\\Qfoo\\E", of("|foo").keys("g*").lastSearch()); checkBeep("|  ", "*"); }
```
- [ ] **Steps 2–4:** Run (FAIL), implement, then run (PASS). Then check by hand in jEdit:
  - `*` then `n`/`N`; `#` then `n` goes backward.
  - `?foo` then `n` goes backward, and `N` goes forward.
- [ ] **Step 5: Commit** `feat: * # g* g# search and correct n/N direction`

---

### Task 13: Insert-mode `C-w`, `C-u`, `C-o`

**Files:**
- Create: `src/vimulator/core/InsertEdits.java`, `test/vimulator/core/InsertEditsTest.java`.
- Modify:
  - `actions.xml`: add actions `vi-insert-delete-word`, `vi-insert-delete-line` and `vi-insert-one-command`.
  - `Vimulator_keys.props`: add `vimulator.keys.insert.vi-insert-delete-word=C+W`, `vimulator.keys.insert.vi-insert-delete-line=C+U` and `vimulator.keys.insert.vi-insert-one-command=C+O`.
  - `VimulatorInputHandler.java` and `BindingInputHandler.java`.
  - `VimulatorUtilities.java`: add a `setMode` variant that doesn't move the caret left.

**Interfaces:**
- Produces:
  - `InsertEdits.deleteWordStart(TextModel t, CharClass cc, int caret) -> int`:
    - At a line start, returns `caret-1`, which joins with the previous line; at offset 0 returns 0.
    - Otherwise it skips blanks backward, then a run of the class of the char before them.
  - `InsertEdits.deleteLineStart(TextModel t, int caret) -> int`:
    - Returns the first non-blank if the caret is after it.
    - Otherwise returns the line start if the caret is after that.
    - At a line start, returns `caret-1`; at offset 0 returns 0.
  - The jEdit actions remove `[result, caret)` from the buffer.
  - `C-o` sets `VimulatorInputHandler.returnToInsert = true` and switches to COMMAND without moving the caret left. The handler re-enters INSERT after either of these, if the flag is still set:
    - the next `DONE` from the engine, or
    - the next completed legacy action, checked in `BindingInputHandler` after `invokeAction`.

    The flag is cleared first if that command itself left the mode at INSERT or a visual mode.

- [ ] **Step 1: Write failing tests.** `m(s)` builds a `FakeEditor` from fixture notation, and `cc = new CharClass("")`:

```java
public static void testCtrlW() { eq(4, InsertEdits.deleteWordStart(m("foo bar|"), cc, 7)); eq(4, InsertEdits.deleteWordStart(m("foo bar  |"), cc, 9)); eq(7, InsertEdits.deleteWordStart(m("foo.bar..|"), cc, 9)); eq(3, InsertEdits.deleteWordStart(m("foo\n|x"), cc, 4)); eq(0, InsertEdits.deleteWordStart(m("|"), cc, 0)); }
public static void testCtrlU() { eq(2, InsertEdits.deleteLineStart(m("  ab|"), 4)); eq(0, InsertEdits.deleteLineStart(m("  |ab"), 2)); eq(1, InsertEdits.deleteLineStart(m("a\n|b"), 2)); }
```
- [ ] **Steps 2–4:** Run (FAIL), implement, then run (PASS). Then check by hand in jEdit:
  - In insert mode, `C-w` and `C-u` delete as described.
  - `C-o $` lands at end of line and is back in insert mode; `C-o zz` scrolls and stays in insert mode.
- [ ] **Step 5: Commit** `feat: insert-mode C-w, C-u and C-o`

---

### Task 14: Ex ranges, `:s`, `:noh`, `:x` and broken ex commands

**Files:**
- Create: `src/vimulator/core/ex/{VimRegex,ExRange,SubstituteCommand,Substitute}.java`, `test/vimulator/core/ExTest.java`.
- Modify:
  - `src/vimulator/ex/ExInterpreter.java`.
  - `src/vimulator/VimulatorCommandLine.java`: add `beginExCommand(String initial)`.
  - `actions.xml`: add `vi-save-close-buffer-force`, `vi-save-close-all`, `vi-save-close-all-force`, `vi-close-all-force` and `vi-visual-ex-command`.
  - `Vimulator_keys.props`: add `vimulator.keys.visual.vi-visual-ex-command=:`. The action calls `beginExCommand("'<,'>")` and then sets command mode.

**Interfaces:**
- Produces:
  - `VimRegex.toJava(String vim) -> String` (magic mode):
    - These become Java metacharacters: `\(` `\)` `\|` `\+` `\?` `\=` (→ `?`) `\{…}`, and `\<` `\>` (→ `\b`).
    - These bare chars become escaped literals: `( ) | + ? { }`.
    - These pass through unchanged: `. * [ ] ^ $ \s \d \w`.
    - A leading `\v` switches to very-magic: the rest passes through as Java syntax, except that `<` and `>` become `\b`.
  - `VimRegex.replacementToJava(String vim) -> String`: `&` and `\0` → `$0`; `\1`–`\9` → `$1`–`$9`; `\&` → literal `&`; `\r` → newline; `\\` → backslash; `$` → literal `$`.
  - `record ExRange(int first, int last, String rest)`, built by `static ExRange parse(String cmd, int currentLine, int lastLine, int[] visualLines)`. Lines are 0-based.
    - Address items: `%`, `.`, `$`, `N` (1-based), `'<`, `'>`, and `+N`/`-N` offsets, either standalone or after an item.
    - `a,b` gives a range; a reversed range is swapped. With no address, the range is (current, current).
    - A `'<` without visual lines throws `IllegalArgumentException`.
  - `record SubstituteCommand(String pattern, String replacement, boolean global, boolean ignoreCase)`, built by `static SubstituteCommand parse(String rest)`.
    - It accepts `s` or `substitute`, followed by any non-alphanumeric delimiter. The trailing delimiter is optional.
    - Flags are `g`, `i` and `I`.
  - `Substitute.apply(List<String> lines, int first, int last, String javaRegex, String javaReplacement, boolean global, boolean ignoreCase) -> Substitute.Result(List<String> lines, int substitutions, int linesChanged, int lastChangedLine)`. Matching is per line, with no multi-line matches.
  - `ExInterpreter.eval` order:
    1. Parse the range.
    2. If `rest` is empty and an address was given, go to `last`.
    3. If `rest` is `s`/`substitute…`, substitute. An empty pattern uses the `'/'` register.
    4. `noh`/`nohlsearch` is a no-op.
    5. Otherwise use the existing command table; a range combined with a non-range command is a `BadExCommandException`.
  - `:s` details:
    - It edits the buffer in one compound edit, replacing only the changed lines.
    - The status line shows `"N substitutions on M lines"`; zero matches gives the status `"Pattern not found"` and a beep.
    - The caret moves to the first non-blank of `lastChangedLine`.
  - Add `bind("x", "vi-save-close-buffer")`.
  - New actions:
    - `vi-close-all-force`: `setDirty(false)` on every buffer, then `jEdit.closeAllBuffers(view)`.
    - `vi-save-close-all` / `-force`: `jEdit.saveAllBuffers(view, false)`, then `jEdit.closeAllBuffers(view)`.
    - `vi-save-close-buffer-force`: `buffer.save(view, null, true)`, then `jEdit.closeBuffer(view, buffer)`.
  - `:'<,'>` reads `engine.lastVisualLines()`.

- [ ] **Step 1: Write failing tests:**

```java
public static void testVimRegex() { eq("foo(bar)", VimRegex.toJava("foo\\(bar\\)")); eq("a\\+b", VimRegex.toJava("a+b")); eq("\\bx\\b", VimRegex.toJava("\\<x\\>")); eq("a|b", VimRegex.toJava("a\\|b")); eq("(a|b)+", VimRegex.toJava("\\v(a|b)+")); eq("x{2,3}", VimRegex.toJava("x\\{2,3}")); }
public static void testReplacement() { eq("[$0]", VimRegex.replacementToJava("[&]")); eq("$1-", VimRegex.replacementToJava("\\1-")); eq("a\nb", VimRegex.replacementToJava("a\\rb")); eq("\\$", VimRegex.replacementToJava("$")); }
public static void testRange() { eq(new ExRange(0, 9, "s/a/b/"), ExRange.parse("%s/a/b/", 3, 9, null)); eq(new ExRange(3, 3, "s/a/b/"), ExRange.parse("s/a/b/", 3, 9, null)); eq(new ExRange(1, 4, "d"), ExRange.parse("2,5d", 3, 9, null)); eq(new ExRange(3, 5, "s/x/y"), ExRange.parse(".,+2s/x/y", 3, 9, null)); eq(new ExRange(4, 6, "s/a/b"), ExRange.parse("'<,'>s/a/b", 0, 9, new int[]{4, 6})); eq(new ExRange(9, 9, ""), ExRange.parse("$", 0, 9, null)); eq(new ExRange(1, 4, ""), ExRange.parse("5,2", 0, 9, null)); }
public static void testParseSub() { SubstituteCommand c = SubstituteCommand.parse("s#a/b#c#gi"); eq("a/b", c.pattern()); eq("c", c.replacement()); eq(true, c.global()); eq(true, c.ignoreCase()); eq("", SubstituteCommand.parse("s//x").pattern()); }
public static void testSubstitute() { var r = Substitute.apply(List.of("aa", "ba", "c"), 0, 2, "a", "x", false, false); eq(List.of("xa", "bx", "c"), r.lines()); eq(2, r.substitutions()); eq(1, r.lastChangedLine()); eq(List.of("xx"), Substitute.apply(List.of("aA"), 0, 0, "a", "x", true, true).lines()); eq(0, Substitute.apply(List.of("b"), 0, 0, "a", "x", true, false).substitutions()); }
```
- [ ] **Steps 2–4:** Run (FAIL), implement, then run (PASS). Then check by hand in jEdit:
  - `:%s/foo/bar/g`, `:s/\(a\)b/\1/`, `V` + `jj` + `:` (prefilled `'<,'>`) + `s/x/y/`, then a single `u` undoes it.
  - `:noh`; `:5`.
  - `:wq!`, `:wqa` and `:qa!` work on a scratch buffer.
- [ ] **Step 5: Commit** `feat(ex): ranges, :s with Vim regex, :noh, :x and fixed :wq!/:wqa/:qa!`

---

### Task 15: Remove superseded legacy bindings and code, update docs

**Files:** Modify `Vimulator_keys.props`, `actions.xml`, `src/vimulator/VimulatorUtilities.java`, `src/vimulator/VimulatorPlugin.java`, `README.md`, `TODO.txt`, and `Vimulator.props` (minor bump of `plugin.vimulator.VimulatorPlugin.version`). Create `test/vimulator/KeymapConsistencyTest.java`.

**Interfaces:**
- Consumes: everything above.
- Produces:
  - Delete from the `command`, `visual` and `visual-line` sections every binding whose key the engine now owns:
    - Motions: `h l j k 0 ^ $ - + w b e W B E gg G { } % f F t T ; , m `` '` (arrow and ctrl aliases included).
    - Editing: `x X J D C s S Y r`, every `D …`, `C …` and `Y …` sequence, `<< >>`.
    - Insert and visual entry: `i a I A o O v V C+V`.
    - The visual select-* bindings and `skip`, plus `visual-line.vi-multiline-change`.
  - Keep legacy:
    - Search and ex entry: `: / ? n N`; `Q`; `C-g`.
    - Undo/redo and paste: `u C+R p P`.
    - `C-w` window commands and `z f/m/o/r` folds.
    - `PAGE_UP/DOWN C-f C-b C+HOME C+END`.
    - Visual `I A p r`, `<Esc>`/`C-[`/`C-c`, visual-block `c`, and the `global` section.
    - All insert-mode bindings.
  - Delete the actions in `actions.xml` that no binding or ex command references any more, and their `VimulatorUtilities` helpers. A helper still used by a kept action stays.
  - Remove `VimulatorPlugin.CharacterSearch`/`lastFindChar` and the `markLines`/`markColumns` arrays.
  - README: replace the feature bullets with a "Supported keys" table grouped as motions, operators, text objects, visual, scrolling, insert and ex, plus a "Not supported" line (registers, macros, `d/pattern`, visual `.`). Document `scripts/build.sh`, `scripts/test.sh` and `JAVA_HOME`.
  - TODO.txt: remove the items this plan fixed (`D E`, `G G` count, `f/t` PRESSED keys).

- [ ] **Step 1: Write the check** `KeymapConsistencyTest`. It reads files relative to the repo root:

```java
public static void testEveryBindingHasAnAction() { for (String a : boundActions("Vimulator_keys.props")) if (a.startsWith("vi-")) assertTrue(a, definedActions("actions.xml").contains(a)); }
public static void testEveryExBindingHasAnAction() { for (String a : exBoundActions("src/vimulator/ex/ExInterpreter.java")) if (a.startsWith("vi-")) assertTrue(a, definedActions("actions.xml").contains(a)); }
```
The helpers work as follows:
- `boundActions` parses `vimulator.keys.<mode>.<action>[.N]=…`.
- `exBoundActions` greps `bind("…", "<action>")`.
- `definedActions` greps `ACTION NAME="…"`.

- [ ] **Step 2: Run `scripts/test.sh`.** Expected: FAIL, listing the bindings that are dangling today (`vi-delete-next-word`, `vi-change-next-word`, …).
- [ ] **Step 3: Do the deletions and doc edits above.**
- [ ] **Step 4: Run `scripts/test.sh` (all pass) and `scripts/build.sh`.** Then do a full manual pass in jEdit over the README table, including that these legacy keys still work: `:w`, `/x n`, `u`, `C-r`, `p`, `C-w v`, `zf`, visual `p`/`r`, and block `I`.
- [ ] **Step 5: Commit** `refactor: drop superseded per-combination actions; document supported keys`
