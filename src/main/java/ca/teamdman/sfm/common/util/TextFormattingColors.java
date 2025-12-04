package ca.teamdman.sfm.common.util;

import net.minecraft.util.text.TextFormatting;

import java.util.HashMap;
import java.util.Map;

public class TextFormattingColors {
    private static final Map<TextFormatting, Integer> FORMAT_COLOR_MAP = new HashMap<>();

    static {
        FORMAT_COLOR_MAP.put(TextFormatting.BLACK, 0x000000);
        FORMAT_COLOR_MAP.put(TextFormatting.DARK_BLUE, 0x0000AA);
        FORMAT_COLOR_MAP.put(TextFormatting.DARK_GREEN, 0x00AA00);
        FORMAT_COLOR_MAP.put(TextFormatting.DARK_AQUA, 0x00AAAA);
        FORMAT_COLOR_MAP.put(TextFormatting.DARK_RED, 0xAA0000);
        FORMAT_COLOR_MAP.put(TextFormatting.DARK_PURPLE, 0xAA00AA);
        FORMAT_COLOR_MAP.put(TextFormatting.GOLD, 0xFFAA00);
        FORMAT_COLOR_MAP.put(TextFormatting.GRAY, 0xAAAAAA);
        FORMAT_COLOR_MAP.put(TextFormatting.DARK_GRAY, 0x555555);
        FORMAT_COLOR_MAP.put(TextFormatting.BLUE, 0x5555FF);
        FORMAT_COLOR_MAP.put(TextFormatting.GREEN, 0x55FF55);
        FORMAT_COLOR_MAP.put(TextFormatting.AQUA, 0x55FFFF);
        FORMAT_COLOR_MAP.put(TextFormatting.RED, 0xFF5555);
        FORMAT_COLOR_MAP.put(TextFormatting.LIGHT_PURPLE, 0xFF55FF);
        FORMAT_COLOR_MAP.put(TextFormatting.YELLOW, 0xFFFF55);
        FORMAT_COLOR_MAP.put(TextFormatting.WHITE, 0xFFFFFF);
    }

    public static int getColorCode(TextFormatting fmt) {
        return FORMAT_COLOR_MAP.getOrDefault(fmt, 0xFFFFFF);
    }
}
