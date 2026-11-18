package vimulator.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Pure text-object functions. Each returns the selected {@link Range}, or null when there is
 * nothing to select. An empty interior is returned as an empty EXCLUSIVE range.
 */
public final class TextObjects {
    private TextObjects() {}

    private static int cls(TextModel t, CharClass cc, int i, boolean big) {
        char c = t.charAt(i);
        return big ? cc.ofBig(c) : cc.of(c);
    }

    private static boolean onChar(TextModel t, int caret) {
        return caret < t.length() && t.charAt(caret) != '\n';
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t';
    }

    // ---- words

    /** {@code iw}/{@code iW}: count runs of same-class characters, starting at the caret's run. */
    public static Range innerWord(TextModel t, CharClass cc, int caret, int count, boolean big) {
        if (!onChar(t, caret)) return null;
        int line = Motions.lineOf(t, caret);
        int ls = t.lineStart(line), le = t.lineEnd(line);
        int c0 = cls(t, cc, caret, big);
        int start = caret;
        while (start > ls && cls(t, cc, start - 1, big) == c0) start--;
        int end = runEnd(t, cc, caret, le, big);
        for (int n = 1; n < count && end + 1 < le; n++) end = runEnd(t, cc, end + 1, le, big);
        return new Range(start, end, MotionType.INCLUSIVE);
    }

    private static int runEnd(TextModel t, CharClass cc, int i, int le, boolean big) {
        int c0 = cls(t, cc, i, big);
        while (i + 1 < le && cls(t, cc, i + 1, big) == c0) i++;
        return i;
    }

    /** {@code aw}/{@code aW}: words plus trailing blanks, or leading blanks if there are none. */
    public static Range aWord(TextModel t, CharClass cc, int caret, int count, boolean big) {
        if (!onChar(t, caret)) return null;
        int line = Motions.lineOf(t, caret);
        int ls = t.lineStart(line), le = t.lineEnd(line);
        int start = caret;
        int c0 = cls(t, cc, caret, big);
        while (start > ls && cls(t, cc, start - 1, big) == c0) start--;
        int end = caret;
        boolean trailing = false;
        for (int n = 0; n < count; n++) {
            if (n > 0 && end + 1 >= le) break;
            int i = n == 0 ? caret : end + 1;
            if (cls(t, cc, i, big) == CharClass.BLANK) {
                // on blanks: the blanks plus the following word
                end = runEnd(t, cc, i, le, big);
                if (end + 1 < le) end = runEnd(t, cc, end + 1, le, big);
                trailing = true;
            } else {
                end = runEnd(t, cc, i, le, big);
                if (end + 1 < le && isBlank(t.charAt(end + 1))) {
                    end = runEnd(t, cc, end + 1, le, big);
                    trailing = true;
                }
            }
        }
        if (!trailing) {
            while (start > ls && isBlank(t.charAt(start - 1))) start--;
        }
        return new Range(start, end, MotionType.INCLUSIVE);
    }

    // ---- quotes (line-limited)

    /** {@code i"}/{@code a"} and friends for quote character q. */
    public static Range quote(TextModel t, int caret, char q, boolean around) {
        int line = Motions.lineOf(t, caret);
        int ls = t.lineStart(line), le = t.lineEnd(line);
        List<Integer> quotes = new ArrayList<>();
        for (int i = ls; i < le; i++) {
            if (t.charAt(i) == q && !escaped(t, ls, i)) quotes.add(i);
        }
        int open = -1, close = -1;
        for (int k = 0; k + 1 < quotes.size(); k += 2) {
            int a = quotes.get(k), b = quotes.get(k + 1);
            if (a <= caret && caret <= b) {
                open = a;
                close = b;
                break;
            }
            if (a > caret && open < 0) {
                open = a;
                close = b;
                break;
            }
        }
        if (open < 0) return null;
        if (!around) {
            if (close == open + 1) return new Range(open + 1, open + 1, MotionType.EXCLUSIVE);
            return new Range(open + 1, close - 1, MotionType.INCLUSIVE);
        }
        int start = open, end = close;
        if (end + 1 < le && isBlank(t.charAt(end + 1))) {
            while (end + 1 < le && isBlank(t.charAt(end + 1))) end++;
        } else {
            while (start > ls && isBlank(t.charAt(start - 1))) start--;
        }
        return new Range(start, end, MotionType.INCLUSIVE);
    }

    private static boolean escaped(TextModel t, int ls, int i) {
        int n = 0;
        for (int j = i - 1; j >= ls && t.charAt(j) == '\\'; j--) n++;
        return n % 2 == 1;
    }

    // ---- brackets

    /** {@code i(}/{@code a(} and friends: the count-th pair of open/close enclosing the caret. */
    public static Range bracket(TextModel t, int caret, int count, char open, char close, boolean around) {
        if (t.length() == 0) return null;
        int pos = Math.min(caret, t.length() - 1);
        int o;
        char at = t.charAt(pos);
        if (at == open) o = pos;
        else if (at == close) o = Motions.scanBackward(t, pos, open, close);
        else o = unmatchedOpenBefore(t, pos - 1, open, close);
        for (int n = 1; n < count && o >= 0; n++) o = unmatchedOpenBefore(t, o - 1, open, close);
        if (o < 0) return null;
        int c = Motions.scanForward(t, o, open, close);
        if (c < 0) return null;
        if (around) return new Range(o, c, MotionType.INCLUSIVE);

        int openLine = t.lineOfOffset(o), closeLine = t.lineOfOffset(c);
        boolean openAtEol = o + 1 == t.lineEnd(openLine);
        boolean closeAtBol = closeLine > openLine && Motions.firstNonBlank(t, closeLine) == c;
        if (openAtEol && closeAtBol) {
            int first = openLine + 1, last = closeLine - 1;
            if (first > last) return new Range(c, c, MotionType.EXCLUSIVE);
            return new Range(t.lineStart(first), t.lineStart(last), MotionType.LINEWISE);
        }
        int start = openAtEol && closeLine > openLine ? t.lineStart(openLine + 1) : o + 1;
        if (start >= c) return new Range(o + 1, o + 1, MotionType.EXCLUSIVE);
        return new Range(start, c - 1, MotionType.INCLUSIVE);
    }

    private static int unmatchedOpenBefore(TextModel t, int from, char open, char close) {
        int depth = 0;
        for (int i = from; i >= 0; i--) {
            char ch = t.charAt(i);
            if (ch == close) depth++;
            else if (ch == open) {
                if (depth == 0) return i;
                depth--;
            }
        }
        return -1;
    }

    // ---- paragraphs

    private static boolean blankLine(TextModel t, int line) {
        int s = t.lineStart(line), e = t.lineEnd(line);
        for (int i = s; i < e; i++) if (!isBlank(t.charAt(i))) return false;
        return true;
    }

    /** {@code ip}/{@code ap}: runs of blank or non-blank lines. */
    public static Range paragraph(TextModel t, int caret, int count, boolean around) {
        int lastLine = t.lineCount() - 1;
        int line = Motions.lineOf(t, caret);
        boolean kind = blankLine(t, line);
        int first = line;
        while (first > 0 && blankLine(t, first - 1) == kind) first--;
        int last = runLast(t, line, lastLine);
        for (int n = 1; n < count && last < lastLine; n++) last = runLast(t, last + 1, lastLine);
        if (around) {
            if (last < lastLine) {
                last = runLast(t, last + 1, lastLine);
            } else if (!kind) {
                while (first > 0 && blankLine(t, first - 1)) first--;
            }
        }
        return new Range(t.lineStart(first), t.lineStart(last), MotionType.LINEWISE);
    }

    private static int runLast(TextModel t, int line, int lastLine) {
        boolean kind = blankLine(t, line);
        while (line < lastLine && blankLine(t, line + 1) == kind) line++;
        return line;
    }

    // ---- tags

    private record Tag(int openStart, int openEnd, int closeStart, int closeEnd) {
    }

    /** {@code it}/{@code at}: the count-th enclosing {@code <name ...>...</name>} pair. */
    public static Range tag(TextModel t, int caret, int count, boolean around) {
        List<Tag> enclosing = new ArrayList<>();
        for (Tag tag : pairTags(t)) {
            if (tag.openStart <= caret && caret < tag.closeEnd) enclosing.add(tag);
        }
        enclosing.sort((a, b) -> Integer.compare(b.openStart, a.openStart));
        if (enclosing.size() < count) return null;
        Tag tag = enclosing.get(count - 1);
        if (around) return new Range(tag.openStart, tag.closeEnd - 1, MotionType.INCLUSIVE);
        if (tag.openEnd == tag.closeStart) return new Range(tag.openEnd, tag.openEnd, MotionType.EXCLUSIVE);
        return new Range(tag.openEnd, tag.closeStart - 1, MotionType.INCLUSIVE);
    }

    private static List<Tag> pairTags(TextModel t) {
        List<Tag> out = new ArrayList<>();
        Deque<Object[]> stack = new ArrayDeque<>(); // {name, openStart, openEnd}
        int len = t.length();
        for (int i = 0; i < len; i++) {
            if (t.charAt(i) != '<') continue;
            int gt = i + 1;
            while (gt < len && t.charAt(gt) != '>' && t.charAt(gt) != '<') gt++;
            if (gt >= len || t.charAt(gt) != '>') continue;
            boolean closing = i + 1 < gt && t.charAt(i + 1) == '/';
            int nameStart = closing ? i + 2 : i + 1;
            int nameEnd = nameStart;
            while (nameEnd < gt && !Character.isWhitespace(t.charAt(nameEnd)) && t.charAt(nameEnd) != '/') nameEnd++;
            if (nameEnd == nameStart || !Character.isLetter(t.charAt(nameStart))) continue;
            StringBuilder name = new StringBuilder();
            for (int k = nameStart; k < nameEnd; k++) name.append(t.charAt(k));
            if (closing) {
                // pop to the matching open tag, discarding unclosed ones in between
                for (Object[] top : stack) {
                    if (top[0].equals(name.toString())) {
                        while (true) {
                            Object[] popped = stack.pop();
                            if (popped == top) break;
                        }
                        out.add(new Tag((int) top[1], (int) top[2], i, gt + 1));
                        break;
                    }
                }
            } else if (t.charAt(gt - 1) != '/') {
                stack.push(new Object[] {name.toString(), i, gt + 1});
            }
            i = gt;
        }
        return out;
    }
}
