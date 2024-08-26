package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.TestBarrelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TestBarrelBlock extends BarrelBlock {
    public TestBarrelBlock() {
        super(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD));
    }

    @Override
    public void onRemove(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            BlockState pNewState,
            boolean pIsMoving
    ) {
        if (!pState.is(pNewState.getBlock())) {
            pLevel.removeBlockEntity(pPos);
            super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos pPos,
            BlockState pState
    ) {
        return new TestBarrelBlockEntity(pPos, pState);
    }
}
