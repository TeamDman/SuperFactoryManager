package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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
    public IFacadableBlock getNonFacadeBlock() {
        return SFMBlocks.TUNNELLED_FANCY_CABLE_BLOCK.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {
        return SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE_BLOCK.get();
    }
}
