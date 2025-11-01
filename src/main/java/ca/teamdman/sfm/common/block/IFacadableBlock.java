package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.facade.FacadeTransparency;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.block.Block;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public interface IFacadableBlock {
    IFacadableBlock getNonFacadeBlock();

    IFacadableBlock getFacadeBlock();

    BlockState getStateForPlacementByFacadePlan(
            LevelAccessor level,
            BlockPos pos
    );

    default void createFacadeBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY);
        builder.add(LightBlock.LEVEL);
    }
}
