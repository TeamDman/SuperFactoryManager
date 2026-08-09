package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

public class TunnelledFancyCableBlockEntity extends BlockEntity {
    public TunnelledFancyCableBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(SFMBlockEntities.TUNNELLED_FANCY_CABLE.get(), blockPos, blockState);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!(this.level instanceof ServerLevel lvl)) {
            return LazyOptional.empty();
        }

        if (side == null) {
            return super.getCapability(cap, null);
        }

        BlockEntity be = lvl.getBlockEntity(this.getBlockPos().offset(side.getOpposite().getNormal()));
        if (be == null) {
            return LazyOptional.empty();
        }

        return be.getCapability(cap, side);
    }
}
