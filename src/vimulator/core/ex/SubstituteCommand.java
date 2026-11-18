package vimulator.core.ex;

/** A parsed {@code :s/pattern/replacement/flags} command (pattern and replacement still in Vim syntax). */
public record SubstituteCommand(String pattern, String replacement, boolean global, boolean ignoreCase) {

    /** @return the command, or null when {@code rest} is not a substitute command */
    public static SubstituteCommand parse(String rest) {
        int i;
        if (rest.startsWith("substitute")) i = "substitute".length();
        else if (rest.startsWith("s")) i = 1;
        else return null;
        if (i >= rest.length()) return null;
        char delim = rest.charAt(i);
        if (Character.isLetterOrDigit(delim) || Character.isWhitespace(delim) || delim == '\\'
                || delim == '"' || delim == '|')
            return null;
        StringBuilder pattern = new StringBuilder(), replacement = new StringBuilder();
        i = readPart(rest, i + 1, delim, pattern);
        i = readPart(rest, i, delim, replacement);
        boolean global = false, ignoreCase = false;
        for (; i < rest.length(); i++) {
            char f = rest.charAt(i);
            if (f == 'g') global = true;
            else if (f == 'i') ignoreCase = true;
            else if (f == 'I') ignoreCase = false;
        }
        return new SubstituteCommand(pattern.toString(), replacement.toString(), global, ignoreCase);
    }

    /** Reads up to the next unescaped delimiter; an escaped delimiter becomes literal. */
    private static int readPart(String s, int i, char delim, StringBuilder out) {
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char d = s.charAt(i + 1);
                if (d == delim) out.append(d);
                else out.append(c).append(d);
                i++;
            } else if (c == delim) {
                return i + 1;
            } else {
                out.append(c);
            }
        }
        return i;
    }
}
