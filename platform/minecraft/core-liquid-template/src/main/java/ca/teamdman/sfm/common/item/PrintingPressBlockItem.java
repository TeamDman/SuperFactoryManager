package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
{% case minecraft_version %}
{% when '1.19.2' %}
import ca.teamdman.sfm.common.registry.registration.SFMCreativeTabs;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.ChatFormatting;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.core.component.DataComponentGetter;
{% endcase %}
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.item.ItemStack;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.world.item.TooltipFlag;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
{% when '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.item.component.TooltipProvider;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import java.util.List;
{% when '26.1.2' %}
import java.util.function.Consumer;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public class PrintingPressBlockItem extends BlockItem {
{% when '26.1.2' %}
public class PrintingPressBlockItem extends BlockItem implements TooltipProvider {
{% endcase %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry PRINTING_PRESS_TOOLTIP = new LocalizationEntry(
            () -> SFMItems.PRINTING_PRESS.get().getDescriptionId() + ".tooltip",
            () -> "Place with an air gap below a downward facing piston. Extend the piston to use."
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public PrintingPressBlockItem() {
{% when '26.1.2' %}
    public PrintingPressBlockItem(Properties properties) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2' %}
        super(SFMBlocks.PRINTING_PRESS.get(), new Properties().tab(SFMCreativeTabs.MAIN));
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super(SFMBlocks.PRINTING_PRESS.get(), new Properties());
{% when '26.1.2' %}
        super(SFMBlocks.PRINTING_PRESS.get(), properties);
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public void appendHoverText(
            ItemStack pStack,
            @Nullable Level pLevel,
            List<Component> pTooltip,
            TooltipFlag pFlag
    ) {

        super.appendHoverText(pStack, pLevel, pTooltip, pFlag);
        pTooltip.add(PRINTING_PRESS_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
{% when '1.21', '1.21.1' %}
    public void appendHoverText(
            ItemStack pStack,
            TooltipContext pContext,
            List<Component> pTooltipComponents,
            TooltipFlag pTooltipFlag
    ) {
        super.appendHoverText(pStack, pContext, pTooltipComponents, pTooltipFlag);
        pTooltipComponents.add(PRINTING_PRESS_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
{% when '26.1.2' %}
    public void addToTooltip(TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        consumer.accept(
                PRINTING_PRESS_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY)
        );
{% endcase %}
    }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}

{% when '26.1.2' %}
{% endcase %}
}
