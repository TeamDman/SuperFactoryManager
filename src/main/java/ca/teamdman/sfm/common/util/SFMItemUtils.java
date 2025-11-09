package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class SFMItemUtils {
    public static void appendMoreInfoKeyReminderTextIfOnClient(List<String> lines) {
        if (SFMEnvironmentUtils.isClient()) {
            lines.add(
                    LocalizationKeys.GUI_ADVANCED_TOOLTIP_HINT.getComponent(
                                  new TextComponentString(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY
                                            .getDisplayName()
                                            ).setStyle(new Style().setColor(TextFormatting.AQUA)))
                            .setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
        }
    }

    public static boolean isClientAndMoreInfoKeyPressed() {
        return SFMEnvironmentUtils.isClient() && SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY);
    }

//    public static MutableComponent getRainbow(int length) {
//        var start = Component.empty();
//        ChatFormatting[] rainbowColors = new ChatFormatting[]{
//                ChatFormatting.DARK_RED,
//                ChatFormatting.RED,
//                ChatFormatting.GOLD,
//                ChatFormatting.YELLOW,
//                ChatFormatting.DARK_GREEN,
//                ChatFormatting.GREEN,
//                ChatFormatting.DARK_AQUA,
//                ChatFormatting.AQUA,
//                ChatFormatting.DARK_BLUE,
//                ChatFormatting.BLUE,
//                ChatFormatting.DARK_PURPLE,
//                ChatFormatting.LIGHT_PURPLE
//        };
//        int rainbowColorsLength = rainbowColors.length;
//        int fullCycleLength = 2 * rainbowColorsLength - 2;
//        for (int i = 0; i < length - 2; i++) {
//            int cyclePosition = i % fullCycleLength;
//            int adjustedIndex = cyclePosition < rainbowColorsLength
//                                ? cyclePosition
//                                : fullCycleLength - cyclePosition;
//            ChatFormatting color = rainbowColors[adjustedIndex];
//            start = start.append(Component.literal("=").withStyle(color));
//        }
//        return start;
//    }

}
