package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TunnelledFancyCableBlockEntity extends BlockEntity {
    public TunnelledFancyCableBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(SFMBlockEntities.TUNNELLED_FANCY_CABLE.get(), blockPos, blockState);
    }

}
