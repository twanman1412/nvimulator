# Nvimulator

A (Neo)vim emulator for jEdit.

Nvimulator is a fork of [nielstron/vimulator](https://github.com/nielstron/vimulator), which in turn
ports mike dillon's jEdit plugin [Vimulator](http://plugins.jedit.org/plugins/?Vimulator) to modern
JDKs and jEdit 5+ (including [Isabelle/jEdit](https://isabelle.in.tum.de/)). This fork is maintained
separately and is not meant to be merged upstream.

Normal and visual mode keys are handled by a Vim-style command engine:
`[count] operator [count] motion | text-object`, so `d3w`, `2dd`, `ci(`, `ya"`,
`gUiw` or `>ap` work as in (n)vim.

## Supported keys

| Area | Keys |
|---|---|
| Motions | `h j k l` (and arrows), `w b e ge W B E gE`, `0 ^ $ _ + - <CR>`, `gg G`, `{ }`, `%`, `N%`, `f F t T ; ,`, `` `a 'a `` (marks, set with `m`), `H M L` |
| Operators | `d c y > < = g~ gu gU`, doubled for lines (`dd cc yy >> << == g~~ guu gUU`), counts on either side |
| Shorthands | `x X D C s S Y`, `J gJ`, `~`, `r{c}`, `C-a C-x` |
| Text objects | `iw aw iW aW`, ``i" a" i' a' i` a` ``, `i( a( ib ab`, `i[ a[`, `i{ a{ iB aB`, `i< a<`, `it at`, `ip ap` |
| Insert | `i a I A o O` (with counts), `R`; in insert mode `C-w C-u C-o` |
| Repeat | `.` |
| Visual | `v V C-v`, all motions and text objects, `o O gv`, `d x c s y > < = ~ u U J gJ`, `p r`, block `I A c` |
| Scrolling | `C-d C-u C-e C-y C-f C-b`, `zz zt zb z<CR> z. z-` |
| Search | `/ ? n N`, `* # g* g#` |
| Undo | `u C-r` |
| Ex | `:w :wa :q :q! :qa :qa! :wq :wq! :wqa :wqa! :x :e! :bn :bp :reg :N :noh`, `:[range]s/pat/rep/[giI]` with `% . $ N +N -N a,b` and `'<,'>` ranges (press `:` in visual mode) |
| Windows / folds | `C-w s v w o`, `zf zm zo zr` |

`:s` patterns use Vim regex syntax: `\(` `\)` groups, `\|` alternation, `\+` `\?` `\=` `\{n,m}`,
`\<` `\>` word boundaries and `\v` very-magic mode; replacements support `&`, `\1`–`\9` and `\r`.

Not supported (yet): registers (`"a`, `"+`), macros (`q @`), `d/pattern`, `.` after a
visual-mode operator.

## Installation

Nvimulator is not on the jEdit Plugin Manager; install the jar manually [as a "User Plugin"](http://plugins.jedit.org/install.php).
Disable the original Vimulator plugin first: both install their own input handler.

For Isabelle/jEdit 2021 on Linux/Mac, the user plugin path is `$HOME/.isabelle/Isabelle2021/jedit/jars`.
For Isabelle/jEdit 2021 on Windows, the user plugin path is `%HOMEPATH%\.isabelle\Isabelle2021\jedit\jars`.

You may find the corresponding `Nvimulator.jar` under [Releases](https://github.com/twanman1412/nvimulator/releases) or by building the project yourself.

## Building without ant

`scripts/build.sh` builds `install/Nvimulator.jar` with plain `javac`/`jar`.
`JAVA_HOME` selects the JDK and `JEDIT_JAR` the jEdit jar to compile against
(default `/usr/share/java/jedit/jedit.jar`):

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk scripts/build.sh
```

## Tests

- `scripts/test.sh` runs the unit tests of the jEdit-independent command engine
  (`src/nvimulator/core`) against an in-memory editor. No dependencies besides a JDK.
- `scripts/smoke.sh` runs end-to-end key sequences in a throwaway jEdit instance
  (separate settings directory; opens a jEdit window for a few seconds).

## Building

Setup ant to point at the correct jdk and jEdit version in `build.xml`.
Then run
```bash
ant dist
```

to build the jar File.
This file can then be manually installed in jEdit via the Plugin Manager.
