package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.cablenetwork.ICableBlock;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.linting.ProgramLinter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class ManagerBlock extends BlockContainer implements ICableBlock, ITileEntityProvider {
    public static final PropertyBool TRIGGERED = PropertyBool.create("triggered");

    public ManagerBlock() {
        super(Material.PISTON);
        setHardness(2F);
        setSoundType(SoundType.METAL);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, TRIGGERED);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(TRIGGERED) ? 1 : 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        if (meta == 1) return getDefaultState().withProperty(TRIGGERED, true);
        return getDefaultState();
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new ManagerBlockEntity();
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (!(world.getTileEntity(pos) instanceof ManagerBlockEntity mgr)) return;
        if ((world.isRemote)) return;
        { // check redstone for triggers
            boolean isPowered = world.isBlockPowered(pos) || world.isBlockPowered(pos.up());
            boolean debounce = state.getValue(TRIGGERED);
            if (isPowered && !debounce) {
                mgr.trackRedstonePulseUnprocessed();
                world.setBlockState(pos, state.withProperty(TRIGGERED, true), 4);
            } else if (!isPowered && debounce) {
                world.setBlockState(pos, state.withProperty(TRIGGERED, false), 4);
            }
        }
    }


    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof ManagerBlockEntity manager) {
                // update warnings on disk as we open the gui
                var disk = manager.getDisk();
                if (disk != null) {
                    var program = manager.getProgram();
                    if (program != null) {
                        DiskItem.setWarnings(
                                disk,
                                ProgramLinter.gatherWarnings(program, LabelPositionHolder.from(disk), manager)
                        );
                    }
                }
                player.openGui(SFM.instance, CommonProxy.GuiType.MANAGER.ordinal(), world, pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return true;
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        CableNetworkManager.onCablePlaced(world, pos);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof IInventory) {
            InventoryHelper.dropInventoryItems(world, pos, (IInventory) te);
            world.updateComparatorOutputLevel(pos, this);
        }
        CableNetworkManager.onCableRemoved(world, pos);
        super.breakBlock(world, pos, state);
    }
}