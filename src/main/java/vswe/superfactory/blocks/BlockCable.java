package vswe.superfactory.blocks;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.cablenetwork.ICableBlock;

public class BlockCable extends Block implements ICableBlock {
    public BlockCable() {
        super(Material.IRON);
        setSoundType(SoundType.METAL);
        setHardness(0.4F);
    }

    @Override
    public void onBlockAdded(World worldIn, BlockPos pos, IBlockState state) {
        super.onBlockAdded(worldIn, pos, state);
        CableNetworkManager.onCablePlaced(worldIn, pos);
    }


    @Override
    public void onNeighborChange(IBlockAccess world, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(world, pos, neighbor);

        bustCaches(world, pos, neighbor);
    }

    public static void onNeighborChange(IBlockAccess world, BlockPos pos) {
        bustCaches(world, pos, null);
    }

    public static void bustCaches(IBlockAccess world, BlockPos pos, @Nullable BlockPos neighborPos) {
        if (world instanceof World w) {
            var network = CableNetworkManager.getOrRegisterNetworkFromCablePosition(w, pos);
            network.ifPresent(nw -> {
                nw.updateVisualManagers();
                if (neighborPos != null && world.getTileEntity(neighborPos) == null) {
                    nw.bustCapabilityCacheForBlock(neighborPos);
                }
            });
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        CableNetworkManager.onCableRemoved(world, pos);
    }


}
