package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.TunnelledManagerBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
{% when '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.item.component.TooltipProvider;
{% endcase %}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import java.util.List;
{% when '26.1.2' %}
import java.util.function.Consumer;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public class TunnelledManagerBlock extends ManagerBlock {
{% when '26.1.2' %}
public class TunnelledManagerBlock extends ManagerBlock implements TooltipProvider {
{% endcase %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry TUNNELLED_MANAGER_ITEM_TOOLTIP = new LocalizationEntry(
            () -> SFMBlocks.TUNNELLED_MANAGER.get().getDescriptionId() + ".tooltip",
            () -> "Passes capabilities through to the opposite side."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry TUNNELLED_MANAGER_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TUNNELLED_MANAGER.get().getDescriptionId(),
            () -> "Tunnelled Factory Manager"
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    public TunnelledManagerBlock(Properties properties) {
        super(properties);
    }

{% endcase %}
    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}

{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
        return SFMBlockEntities.TUNNELLED_MANAGER
                .get()
                .create(pos, state);
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

        pTooltip.add(TUNNELLED_MANAGER_ITEM_TOOLTIP
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

        pTooltip.add(TUNNELLED_MANAGER_ITEM_TOOLTIP
                             .getComponent()
                             .withStyle(ChatFormatting.GRAY));
    }

{% when '26.1.2' %}
{% endcase %}
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {

        if (level.isClientSide()) return null;
        return createTickerHelper(
                type,
                SFMBlockEntities.TUNNELLED_MANAGER.get(),
                TunnelledManagerBlockEntity::serverTick
        );
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        consumer.accept(
                TUNNELLED_MANAGER_ITEM_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY)
        );
    }
{% endcase %}
}
