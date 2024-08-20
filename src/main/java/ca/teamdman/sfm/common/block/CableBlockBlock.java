package ca.teamdman.sfm.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CableBlockBlock extends CableBlock {
    public CableBlockBlock() {
        super();
    }

    @Override
    protected BlockState getState(BlockState currentState, LevelAccessor level, BlockPos pos) {
        return defaultBlockState();
    }

    @Override
    protected boolean hasConnection(LevelAccessor level, BlockPos pos, Direction direction) {
        return false;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }
}
