package ca.teamdman.sfm.common.blockentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;

public class TunnelledManagerBlockEntity extends ManagerBlockEntity {
    public TunnelledManagerBlockEntity() {
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable EnumFacing side) {
        if (this.world.isRemote) {
            return LazyOptional.empty();
        }

        if (side == null) {
            return super.getCapability(cap, null);
        }

        TileEntity be = this.world.getTileEntity(this.getPos().offset(side.getOpposite()));
        if (be == null) {
            return LazyOptional.empty();
        }

        return be.getCapability(cap, side);
    }
}