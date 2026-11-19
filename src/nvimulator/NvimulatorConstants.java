package nvimulator;

import java.util.Map;
public class NvimulatorConstants {
    
    public static final int COMMAND = 1 << 0;
    public static final int INSERT = 1 << 1;
    public static final int VISUAL = 1 << 2;
    public static final int BLOCK = 1 << 3;
    public static final int LINE = 1 << 4;
    public static final int OVERWRITE = 1 << 5;
    public static final int VISUAL_BLOCK = VISUAL | BLOCK;
    public static final int VISUAL_LINE = VISUAL | LINE;
    public static final int REPLACE = INSERT | OVERWRITE;

    public static final int EX = 1 << 6;

    public static final int[] editorModes = {
        COMMAND,
        INSERT,
        VISUAL,
        VISUAL_BLOCK,
        VISUAL_LINE,
        REPLACE
    };

    public static Map<Integer, String> stringOfMode = Map.ofEntries(
        Map.entry(NvimulatorConstants.COMMAND, "command"),
        Map.entry(NvimulatorConstants.VISUAL, "visual"),
        Map.entry(NvimulatorConstants.VISUAL_BLOCK, "visual_block"),
        Map.entry(NvimulatorConstants.VISUAL_LINE, "visual_line"),
        Map.entry(NvimulatorConstants.INSERT, "insert"),
        Map.entry(NvimulatorConstants.REPLACE, "replace")
    );
}
