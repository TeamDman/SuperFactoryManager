package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.TestBarrelBlockEntity;
import ca.teamdman.sfm.common.util.Stored;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import org.jetbrains.annotations.Nullable;

public class TestBarrelBlock extends BarrelBlock {
    public TestBarrelBlock() {
        super(BlockBehaviour.Properties.of(Material.WOOD).strength(2.5F).sound(SoundType.WOOD));
    }

    @Override
    public void onRemove(
            BlockState pState,
            Level pLevel,
            @Stored BlockPos pPos,
            BlockState pNewState,
            boolean pIsMoving
    ) {
        if (!pState.is(pNewState.getBlock())) {
            // remove the block entity manually to prevent the items from dropping on the ground
            pLevel.removeBlockEntity(pPos);
            super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            @Stored BlockPos pPos,
            BlockState pState
    ) {
        return new TestBarrelBlockEntity(pPos, pState);
    }
}
