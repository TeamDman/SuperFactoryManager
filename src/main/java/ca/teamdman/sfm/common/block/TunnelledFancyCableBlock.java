package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TunnelledFancyCableBlock extends FancyCableBlock implements EntityBlock {
    public TunnelledFancyCableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {
        return SFMBlockEntities.TUNNELLED_FANCY_CABLE_BLOCK_ENTITY.get().create(blockPos, blockState);
    }

    @Override
    public void appendHoverText(
            ItemStack pStack,
            @Nullable BlockGetter pLevel,
            List<Component> pTooltip,
            TooltipFlag pFlag
    ) {
        pTooltip.add(LocalizationKeys.TUNNELLED_CABLE_ITEM_TOOLTIP
                             .getComponent()
                             .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {
        return SFMBlocks.TUNNELLED_FANCY_CABLE_BLOCK.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {
        return SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE_BLOCK.get();
    }
}
