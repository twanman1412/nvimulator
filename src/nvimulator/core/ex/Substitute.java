package nvimulator.core.ex;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Line-by-line regex substitution for {@code :s}. */
public final class Substitute {
    private Substitute() {}

    public record Result(List<String> lines, int substitutions, int linesChanged, int lastChangedLine) {
    }

    public static Result apply(List<String> lines, int first, int last, String javaRegex,
                               String javaReplacement, boolean global, boolean ignoreCase) {
        Pattern p = Pattern.compile(javaRegex, ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
        List<String> out = new ArrayList<>(lines);
        int subs = 0, changed = 0, lastChanged = -1;
        for (int l = first; l <= last && l < lines.size(); l++) {
            Matcher m = p.matcher(lines.get(l));
            StringBuilder sb = new StringBuilder();
            int n = 0;
            while (m.find()) {
                m.appendReplacement(sb, javaReplacement);
                n++;
                if (!global) break;
            }
            if (n == 0) continue;
            m.appendTail(sb);
            out.set(l, sb.toString());
            subs += n;
            changed++;
            lastChanged = l;
        }
        return new Result(out, subs, changed, lastChanged);
    }
}
