package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TestBarrelBlockEntity extends BarrelBlockEntity {
    public TestBarrelBlockEntity(
            BlockPos pPos,
            BlockState pBlockState
    ) {
        super(pPos, pBlockState);
    }

    //    @Override
    @SuppressWarnings("unused") // 1.21.1 only
    public boolean isValidBlockState(BlockState blockState) {
        return SFMBlockEntities.TEST_BARREL_BLOCK_ENTITY.get().isValid(blockState);
    }
}
