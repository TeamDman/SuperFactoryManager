package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
{% if features.tooltip_mode_override %}
import ca.teamdman.sfm.client.tooltip.SFMTooltipModeService;
{% endif %}
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

{% case minecraft_version %}
{% when "26.1.2" %}
import java.util.function.Consumer;
{% else %}
import java.util.List;
{% endcase %}

public class SFMItemUtils {
    @SFMLocalizationDatagen
    public static final LocalizationEntry GUI_ADVANCED_TOOLTIP_HINT = new LocalizationEntry(
            "gui.sfm.advanced.tooltip.hint",
            "Hold %s to know more."
    );
{% if features.tooltip_mode_override %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry GUI_COMPACT_TOOLTIP_HINT = new LocalizationEntry(
            "gui.sfm.advanced.tooltip.compact_hint",
            "Compact mode. Expand or reset via the palette."
    );
{% endif %}

{% case minecraft_version %}
{% when "26.1.2" %}
    public static void appendMoreInfoKeyReminderTextIfOnClient(Consumer<Component> tooltipAdder) {
{% else %}
    public static void appendMoreInfoKeyReminderTextIfOnClient(List<Component> lines) {
{% endcase %}

        if (SFMEnvironmentUtils.isClient()) {
{% if features.tooltip_mode_override %}
            if (SFMTooltipModeService.INSTANCE.mode() == SFMTooltipModeService.Mode.COMPACT) {
                lines.add(GUI_COMPACT_TOOLTIP_HINT.getComponent().withStyle(ChatFormatting.GRAY));
                return;
            }
            if (SFMTooltipModeService.INSTANCE.mode() == SFMTooltipModeService.Mode.EXPANDED) return;
{% endif %}
{% case minecraft_version %}
{% when "26.1.2" %}
            tooltipAdder.accept(
{% else %}
            lines.add(
{% endcase %}
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

{% if features.tooltip_mode_override %}
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

{% else %}
    public static boolean isClientAndMoreInfoKeyPressed() {
        return SFMEnvironmentUtils.isClient() && SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY);
    }

{% endif %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        return ItemStack.isSame(a, b);
{% else %}
        return ItemStack.isSameItem(a, b);
{% endcase %}
    }

    public static boolean isSameItemSameTags(
            ItemStack a,
            ItemStack b
    ) {

{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
        return ItemStack.isSameItemSameComponents(a, b);
{% else %}
        return ItemStack.isSameItemSameTags(a, b);
{% endcase %}
    }

    public static boolean isSameItemSameAmount(
            ItemStack a,
            ItemStack b
    ) {

        return isSameItem(a, b) && a.getCount() == b.getCount();
    }

}
