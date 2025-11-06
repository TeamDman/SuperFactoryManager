package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.TunnelledManagerBlockEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class TunnelledManagerBlock extends ManagerBlock {
    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TunnelledManagerBlockEntity();
    }
}