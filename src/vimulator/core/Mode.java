package vimulator.core;

public enum Mode {
    NORMAL, VISUAL, VISUAL_LINE, VISUAL_BLOCK, INSERT, REPLACE;

    public boolean isVisual() {
        return this == VISUAL || this == VISUAL_LINE || this == VISUAL_BLOCK;
    }
}
