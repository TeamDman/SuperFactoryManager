package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TunnelledFancyCableFacadeBlock extends FancyCableFacadeBlock implements EntityBlock, IFacadableBlock {
    public TunnelledFancyCableFacadeBlock(Properties properties) {
        super(properties.lightLevel(LightBlock.LIGHT_EMISSION));
        registerDefaultState(
                defaultBlockState()
                        .setValue(ca.teamdman.sfm.common.facade.FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY, ca.teamdman.sfm.common.facade.FacadeTransparency.TRANSLUCENT)
                        .setValue(LightBlock.LEVEL, 0)
        );
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {
        return SFMBlockEntities.TUNNELLED_FANCY_CABLE_FACADE_BLOCK_ENTITY.get().create(blockPos, blockState);
    }

    @Override
    public ItemStack getCloneItemStack(
            BlockGetter pLevel,
            BlockPos pPos,
            BlockState pState
    ) {
        return new ItemStack(SFMBlocks.TUNNELLED_FANCY_CABLE_BLOCK.get());
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
