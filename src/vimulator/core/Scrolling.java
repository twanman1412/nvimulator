package vimulator.core;

/**
 * Viewport arithmetic for scrolling commands and screen motions. All values are 0-based lines:
 * {@code n} = line count, {@code v} = visible lines, {@code first} = first visible line,
 * {@code caret} = caret line. Results are {first', caret'} or null when the command fails.
 */
public final class Scrolling {
    private Scrolling() {}

    /** C-d / C-u. */
    public static int[] halfPage(int n, int v, int first, int caret, int amount, boolean down) {
        if (down) {
            if (caret >= n - 1) return null;
            return new int[] {Math.min(first + amount, Math.max(0, n - v)), Math.min(caret + amount, n - 1)};
        }
        if (caret <= 0) return null;
        return new int[] {Math.max(0, first - amount), Math.max(0, caret - amount)};
    }

    /** C-e / C-y: scroll count lines, dragging the caret along only when it would leave the screen. */
    public static int[] lines(int n, int v, int first, int caret, int count, boolean down) {
        if (down) {
            int f = (int) Math.min((long) first + count, n - 1);
            return new int[] {f, Math.max(caret, f)};
        }
        int f = Math.max(0, first - count);
        return new int[] {f, Math.min(caret, f + v - 1)};
    }

    /** zt / zz / zb: first visible line that puts the caret line at the top, middle or bottom. */
    public static int position(int v, int caret, char where) {
        switch (where) {
            case 't': return caret;
            case 'b': return Math.max(0, caret - v + 1);
            default: return Math.max(0, caret - (v - 1) / 2);
        }
    }

    /** H / M / L target line. */
    public static int screenLine(int n, int v, int first, int count, char which) {
        int last = Math.min(first + v - 1, n - 1);
        switch (which) {
            case 'H': return (int) Math.min((long) first + count - 1, last);
            case 'L': return (int) Math.max(last - ((long) count - 1), first);
            default: return first + (Math.min(v, n - first) - 1) / 2;
        }
    }
}
