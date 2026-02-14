package ca.teamdman.sfm.common.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface IFacadableBlock {
    IFacadableBlock getNonFacadeBlock();

    IFacadableBlock getFacadeBlock();

    IBlockState getStateForPlacementByFacadePlan(
            World level,
            BlockPos pos
    );

//    default void createFacadeBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
//        builder.add(FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY);
//        builder.add(LightBlock.LEVEL);
//    }
}
