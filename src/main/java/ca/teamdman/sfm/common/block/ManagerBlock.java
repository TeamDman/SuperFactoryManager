package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.cablenetwork.ICableBlock;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.linting.ProgramLinter;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.util.NotStored;
import ca.teamdman.sfm.common.util.Stored;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.util.EnumHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.World;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManagerBlock extends BlockContainer implements ICableBlock, ITileEntityProvider {
    public static final PropertyBool TRIGGERED = PropertyBool.create("triggered");

    public ManagerBlock() {
        super(Material.PISTON);
        setHardness(2F);
        setSoundType(SoundType.METAL);
    }

    @NotNull
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, TRIGGERED);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new ManagerBlockEntity();
    }

    @Override
    public void observedNeighborChange(IBlockState state, World world, @NotStored BlockPos pos, Block changedBlock, @NotStored BlockPos changedBlockPos) {
        if (!(world.getTileEntity(pos) instanceof ManagerBlockEntity mgr)) return;
        if ((world.isRemote)) return;
        { // check redstone for triggers
            var isPowered = world.isBlockPowered(pos) || world.isBlockPowered(pos.up());
            var debounce = state.getValue(TRIGGERED);
            if (isPowered && !debounce) {
                mgr.trackRedstonePulseUnprocessed();
                world.setBlockState(pos, state.withProperty(TRIGGERED, true), 4);
            } else if (!isPowered && debounce) {
                world.setBlockState(pos, state.withProperty(TRIGGERED, false), 4);
            }
        }
    }


    @Override
    public boolean onBlockActivated(World level, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (
                level.getTileEntity(pos) instanceof ManagerBlockEntity manager
                        && player instanceof EntityPlayerMP serverPlayer
        ) {
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
            player.openGui(SFM.instance, CommonProxy.GuiType, buf -> ManagerContainerMenu.encode(manager, buf));
            return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(
            BlockState state,
            Level level,
            @NotStored BlockPos pos,
            Player player,
            EnumHand hand,
            BlockHitResult hit
    ) {

    }

    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        CableNetworkManager.onCablePlaced(world, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(
            BlockState state,
            Level level,
            @Stored BlockPos pos,
            BlockState newState,
            boolean isMoving
    ) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                Containers.dropContents(level, pos, container);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            CableNetworkManager.onCableRemoved(level, pos);
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }


}
