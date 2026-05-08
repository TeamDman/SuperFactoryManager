package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.TunnelledManagerBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class TunnelledManagerBlock extends ManagerBlock {
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

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return SFMBlockEntities.TUNNELLED_MANAGER
                .get()
                .create(pos, state);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay tooltipDisplay,
            Consumer<Component> pTooltip,
            TooltipFlag detail
    ) {

        pTooltip.accept(TUNNELLED_MANAGER_ITEM_TOOLTIP
                             .getComponent()
                             .withStyle(ChatFormatting.GRAY));
    }

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

}
