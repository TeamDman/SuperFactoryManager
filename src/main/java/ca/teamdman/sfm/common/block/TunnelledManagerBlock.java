package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.TunnelledManagerBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TunnelledManagerBlock extends ManagerBlock {
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return SFMBlockEntities.TUNNELLED_MANAGER_BLOCK_ENTITY
                .get()
                .create(pos, state);
    }

    @Override
    public void appendHoverText(
            ItemStack pStack,
            @Nullable BlockGetter pLevel,
            List<Component> pTooltip,
            TooltipFlag pFlag
    ) {
        pTooltip.add(LocalizationKeys.TUNNELLED_MANAGER_ITEM_TOOLTIP
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
        return createTickerHelper(type, SFMBlockEntities.TUNNELLED_MANAGER_BLOCK_ENTITY.get(), TunnelledManagerBlockEntity::serverTick);
    }
}
