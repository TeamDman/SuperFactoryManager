package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** One bounded terminal transcript line with its presentation colour. */
public record SFMTerminalLine(String text, int color) {
    public static final int DEFAULT_COLOR = 0xFFE8F0F2;
    public static final int CYAN = 0xFF55FFFF;

    public SFMTerminalLine {
        Objects.requireNonNull(text, "text");
    }

    public static SFMTerminalLine plain(String text) {
        return new SFMTerminalLine(text, DEFAULT_COLOR);
    }
}
