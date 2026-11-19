package nvimulator.core.ex;

/** Translates Vim regular expressions ("magic" and {@code \v} very-magic) to java.util.regex. */
public final class VimRegex {
    private VimRegex() {}

    public static String toJava(String vim) {
        if (vim.startsWith("\\v")) return veryMagic(vim.substring(2));
        StringBuilder out = new StringBuilder();
        int n = vim.length();
        for (int i = 0; i < n; i++) {
            char c = vim.charAt(i);
            if (c == '[') {
                i = copyBracket(vim, i, out);
                continue;
            }
            if (c == '\\' && i + 1 < n) {
                char d = vim.charAt(++i);
                switch (d) {
                    case '(': case ')': case '|': case '+': case '?':
                        out.append(d);
                        break;
                    case '=':
                        out.append('?');
                        break;
                    case '{': {
                        int close = vim.indexOf('}', i);
                        if (close < 0) {
                            out.append("\\{");
                            break;
                        }
                        String body = vim.substring(i + 1, close).replace("\\", "");
                        if (body.isEmpty()) out.append('*');
                        else if (body.equals("-")) out.append("*?");
                        else if (body.startsWith("-")) out.append('{').append(body.substring(1)).append("}?");
                        else out.append('{').append(body).append('}');
                        i = close;
                        break;
                    }
                    case '<': case '>':
                        out.append("\\b");
                        break;
                    case 's': case 'S': case 'd': case 'D': case 'w': case 'W': case 'n': case 't':
                        out.append('\\').append(d);
                        break;
                    case 'a':
                        out.append("[A-Za-z]");
                        break;
                    default:
                        if (Character.isLetterOrDigit(d)) out.append(d);
                        else out.append('\\').append(d);
                }
                continue;
            }
            switch (c) {
                case '(': case ')': case '|': case '+': case '?': case '{': case '}':
                    out.append('\\').append(c);
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    /** Copies a [...] collection verbatim; returns the index of its closing bracket. */
    private static int copyBracket(String s, int open, StringBuilder out) {
        int i = open + 1;
        if (i < s.length() && s.charAt(i) == '^') i++;
        if (i < s.length() && s.charAt(i) == ']') i++;
        while (i < s.length() && s.charAt(i) != ']') i++;
        if (i >= s.length()) {
            out.append("\\[");
            return open;
        }
        out.append(s, open, i + 1);
        return i;
    }

    private static String veryMagic(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                out.append('\\').append(s.charAt(++i)); // escaped: literal in Java too
            } else if (c == '<' || c == '>') {
                out.append("\\b");
            } else if (c == '=') {
                out.append('?');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Translates a Vim replacement string to a java.util.regex replacement. */
    public static String replacementToJava(String vim) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < vim.length(); i++) {
            char c = vim.charAt(i);
            if (c == '\\' && i + 1 < vim.length()) {
                char d = vim.charAt(++i);
                if (Character.isDigit(d)) out.append('$').append(d);
                else if (d == 'r' || d == 'n') out.append('\n');
                else if (d == 't') out.append('\t');
                else if (d == '\\') out.append("\\\\");
                else if (d == '$') out.append("\\$");
                else out.append(d);
            } else if (c == '&') {
                out.append("$0");
            } else if (c == '$') {
                out.append("\\$");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
