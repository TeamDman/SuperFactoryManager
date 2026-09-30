package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.ChatFormatting;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.core.component.DataComponentGetter;
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.world.item.ItemStack;
{% when '1.21', '1.21.1' %}
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
{% when '26.1.2' %}
import net.minecraft.world.item.Item;
{% endcase %}
import net.minecraft.world.item.TooltipFlag;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;
{% when '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.item.component.TooltipProvider;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import java.util.List;
{% when '1.21', '1.21.1' %}
import java.util.List; 
{% when '26.1.2' %}
import java.util.function.Consumer;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public class ToughCableBlock extends CableBlock {
{% when '26.1.2' %}
public class ToughCableBlock extends CableBlock implements TooltipProvider {
{% endcase %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry TOUGH_CABLE_ITEM_TOOLTIP = new LocalizationEntry(
            () -> SFMBlocks.TOUGH_CABLE.get().getDescriptionId() + ".tooltip",
            () -> "Resists explosions. Can be facaded as tougher blocks."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry TOUGH_CABLE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TOUGH_CABLE.get().getDescriptionId(),
            () -> "Tough Inventory Cable"
    );

    public ToughCableBlock(Properties properties) {

        super(properties);
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    @Override
    public void appendHoverText(
            ItemStack pStack,
            @Nullable BlockGetter pLevel,
            List<Component> pTooltip,
            TooltipFlag pFlag
    ) {

        pTooltip.add(TOUGH_CABLE_ITEM_TOOLTIP
                             .getComponent()
                             .withStyle(ChatFormatting.GRAY));
    }

{% when '1.21', '1.21.1' %}
    @Override
    public void appendHoverText(
            ItemStack pStack,
            Item.TooltipContext pContext,
            List<Component> pTooltip,
            TooltipFlag pFlag
    ) {

        pTooltip.add(TOUGH_CABLE_ITEM_TOOLTIP
                             .getComponent()
                             .withStyle(ChatFormatting.GRAY));
    }

{% when '26.1.2' %}
{% endcase %}
    @Override
    public IFacadableBlock getNonFacadeBlock() {

        return SFMBlocks.TOUGH_CABLE.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {

        return SFMBlocks.TOUGH_CABLE_FACADE.get();
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        consumer.accept(
                TOUGH_CABLE_BLOCK.getComponent().withStyle(ChatFormatting.GRAY)
        );
    }
{% endcase %}
}
