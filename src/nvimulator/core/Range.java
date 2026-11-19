package nvimulator.core;

/**
 * A region of text. {@code start <= end}. For INCLUSIVE ranges {@code end} is the last
 * included character; for EXCLUSIVE it is one past; for LINEWISE only the lines of
 * {@code start} and {@code end} matter.
 */
public record Range(int start, int end, MotionType type) {
}
