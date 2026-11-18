package vimulator.core.ex;

/**
 * A parsed ex line range (0-based, inclusive) plus the rest of the command.
 * Supports {@code % . $ N 'a 'b +N -N} and {@code a,b}.
 */
public record ExRange(int first, int last, String rest) {

    /**
     * @param visualLines {first, last} of the last visual selection for {@code '<} / {@code '>}, or null
     * @throws IllegalArgumentException for an invalid address
     */
    public static ExRange parse(String cmd, int currentLine, int lastLine, int[] visualLines) {
        Parser p = new Parser(cmd.trim(), currentLine, lastLine, visualLines);
        if (p.peek() == '%') {
            p.pos++;
            return new ExRange(0, lastLine, p.rest());
        }
        Integer a = p.address();
        if (a == null) return new ExRange(currentLine, currentLine, p.rest());
        int b = a;
        if (p.peek() == ',' || p.peek() == ';') {
            p.pos++;
            Integer second = p.address();
            b = second == null ? currentLine : second;
        }
        if (a > b) {
            int t = a;
            a = b;
            b = t;
        }
        return new ExRange(a, b, p.rest());
    }

    private static final class Parser {
        final String s;
        final int cur, last;
        final int[] visual;
        int pos;

        Parser(String s, int cur, int last, int[] visual) {
            this.s = s;
            this.cur = cur;
            this.last = last;
            this.visual = visual;
        }

        char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        String rest() {
            return s.substring(pos).trim();
        }

        Integer address() {
            Integer base = null;
            char c = peek();
            if (c == '.') {
                pos++;
                base = cur;
            } else if (c == '$') {
                pos++;
                base = last;
            } else if (Character.isDigit(c)) {
                int start = pos;
                while (Character.isDigit(peek())) pos++;
                base = Integer.parseInt(s.substring(start, pos)) - 1;
            } else if (c == '\'') {
                char m = pos + 1 < s.length() ? s.charAt(pos + 1) : '\0';
                if (m != '<' && m != '>') throw new IllegalArgumentException("unsupported mark '" + m);
                if (visual == null) throw new IllegalArgumentException("no visual selection");
                pos += 2;
                base = m == '<' ? visual[0] : visual[1];
            }
            while (peek() == '+' || peek() == '-') {
                int sign = peek() == '+' ? 1 : -1;
                pos++;
                int start = pos;
                while (Character.isDigit(peek())) pos++;
                int n = pos > start ? Integer.parseInt(s.substring(start, pos)) : 1;
                base = (base == null ? cur : base) + sign * n;
            }
            if (base != null && (base < 0 || base > last))
                throw new IllegalArgumentException("invalid range");
            return base;
        }
    }
}
