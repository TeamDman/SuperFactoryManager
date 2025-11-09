package ca.teamdman.sfm.common.blockentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;

public class TunnelledManagerBlockEntity extends ManagerBlockEntity {
    public TunnelledManagerBlockEntity() {
    }


    @Override
    @Nullable
    public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing side) {
        if (this.world.isRemote) {
            return null;
        }

        if (side == null) {
            return super.getCapability(cap, null);
        }

        TileEntity be = this.world.getTileEntity(this.getPos().offset(side.getOpposite()));
        if (be == null) {
            return null;
        }

        return be.getCapability(cap, side);
    }
}