package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.tooltip.SFMTooltipModeService;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class SFMItemUtils {
    @SFMLocalizationDatagen
    public static final LocalizationEntry GUI_ADVANCED_TOOLTIP_HINT = new LocalizationEntry(
            "gui.sfm.advanced.tooltip.hint",
            "Hold %s to know more."
    );
    @SFMLocalizationDatagen
    public static final LocalizationEntry GUI_COMPACT_TOOLTIP_HINT = new LocalizationEntry(
            "gui.sfm.advanced.tooltip.compact_hint",
            "Compact mode. Expand or reset via the palette."
    );

    public static void appendMoreInfoKeyReminderTextIfOnClient(List<Component> lines) {

        if (SFMEnvironmentUtils.isClient()) {
            if (SFMTooltipModeService.INSTANCE.mode() == SFMTooltipModeService.Mode.COMPACT) {
                lines.add(GUI_COMPACT_TOOLTIP_HINT.getComponent().withStyle(ChatFormatting.GRAY));
                return;
            }
            if (SFMTooltipModeService.INSTANCE.mode() == SFMTooltipModeService.Mode.EXPANDED) return;
            lines.add(
                    GUI_ADVANCED_TOOLTIP_HINT.getComponent(
                                    SFMKeyMappings.MORE_INFO_TOOLTIP_KEY
                                            .get()
                                            .getTranslatedKeyMessage()
                                            .plainCopy()
                                            .withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY)
            );
        }
    }

    public static boolean isClientAndMoreInfoRequested() {
        return SFMEnvironmentUtils.isClient() && SFMTooltipModeService.INSTANCE.isExpanded();
    }

    /**
     * Physical configured-key state only, retained for compatibility with external callers.
     * @deprecated Tooltip consumers should use {@link #isClientAndMoreInfoRequested()} for semantic mode overrides.
     */
    @Deprecated
    public static boolean isClientAndMoreInfoKeyPressed() {
        return SFMEnvironmentUtils.isClient() && SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY);
    }

    public static MutableComponent getRainbow(int length) {

        var start = Component.empty();
        ChatFormatting[] rainbowColors = new ChatFormatting[]{
                ChatFormatting.DARK_RED,
                ChatFormatting.RED,
                ChatFormatting.GOLD,
                ChatFormatting.YELLOW,
                ChatFormatting.DARK_GREEN,
                ChatFormatting.GREEN,
                ChatFormatting.DARK_AQUA,
                ChatFormatting.AQUA,
                ChatFormatting.DARK_BLUE,
                ChatFormatting.BLUE,
                ChatFormatting.DARK_PURPLE,
                ChatFormatting.LIGHT_PURPLE
        };
        int rainbowColorsLength = rainbowColors.length;
        int fullCycleLength = 2 * rainbowColorsLength - 2;
        for (int i = 0; i < length - 2; i++) {
            int cyclePosition = i % fullCycleLength;
            int adjustedIndex = cyclePosition < rainbowColorsLength
                                ? cyclePosition
                                : fullCycleLength - cyclePosition;
            ChatFormatting color = rainbowColors[adjustedIndex];
            start = start.append(Component.literal("=").withStyle(color));
        }
        return start;
    }

    @MCVersionDependentBehaviour
    public static boolean isSameItem(
            ItemStack a,
            ItemStack b
    ) {

        return ItemStack.isSame(a, b);
    }

    public static boolean isSameItemSameTags(
            ItemStack a,
            ItemStack b
    ) {

        return ItemStack.isSameItemSameTags(a, b);
    }

    public static boolean isSameItemSameAmount(
            ItemStack a,
            ItemStack b
    ) {

        return isSameItem(a, b) && a.getCount() == b.getCount();
    }

}
