package nvimulator.core;

/** Vim-style character classes used by word motions and text objects. */
public final class CharClass {
    public static final int BLANK = 0, PUNCT = 1, WORD = 2;

    private final String extra;

    /** @param extraWordChars additional characters treated as keyword characters (jEdit's noWordSep) */
    public CharClass(String extraWordChars) {
        this.extra = extraWordChars == null ? "" : extraWordChars;
    }

    /** Class for "word" motions: blank, punctuation or keyword. */
    public int of(char c) {
        if (c == ' ' || c == '\t' || c == '\n') return BLANK;
        if (Character.isLetterOrDigit(c) || c == '_' || extra.indexOf(c) >= 0) return WORD;
        return PUNCT;
    }

    /** Class for "WORD" motions: blank or non-blank. */
    public int ofBig(char c) {
        return of(c) == BLANK ? BLANK : WORD;
    }
}
