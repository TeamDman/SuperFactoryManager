package ca.teamdman.sfm.common.util;

import java.util.List;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.localization.LocalizationKeys;

public class SFMItemUtils {
    public static void appendMoreInfoKeyReminderTextIfOnClient(List<String> lines) {
        if (SFMEnvironmentUtils.isClient()) {
            lines.add(
                    LocalizationKeys.GUI_ADVANCED_TOOLTIP_HINT.getComponent(
                                    new TextComponentString(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY
                                            .getDisplayName()).setStyle(new Style().setColor(TextFormatting.AQUA)))
                            .setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText());
        }
    }

    public static boolean isClientAndMoreInfoKeyPressed() {
        return SFMEnvironmentUtils.isClient() && SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY);
    }

       public static ITextComponent getRainbow(int length) {
               var start = new TextComponentString("");
        TextFormatting[] rainbowColors = new TextFormatting[]{
                TextFormatting.DARK_RED,
                TextFormatting.RED,
                TextFormatting.GOLD,
                TextFormatting.YELLOW,
                TextFormatting.DARK_GREEN,
                TextFormatting.GREEN,
                TextFormatting.DARK_AQUA,
                TextFormatting.AQUA,
                TextFormatting.DARK_BLUE,
                TextFormatting.BLUE,
                TextFormatting.DARK_PURPLE,
                TextFormatting.LIGHT_PURPLE
        };
        int rainbowColorsLength = rainbowColors.length;
        int fullCycleLength = 2 * rainbowColorsLength - 2;
        for (int i = 0; i < length - 2; i++) {
            int cyclePosition = i % fullCycleLength;
            int adjustedIndex = cyclePosition < rainbowColorsLength
                    ? cyclePosition
                    : fullCycleLength - cyclePosition;
            TextFormatting color = rainbowColors[adjustedIndex];
            start.appendSibling(new TextComponentString("=").setStyle(new Style().setColor(color)));
        }
        return start;
       }
}
