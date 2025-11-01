package ca.teamdman.sfm.common.block;

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
import net.minecraft.tileentity.TileEntity;
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
import org.jetbrains.annotations.Nullable;

public class ManagerBlock extends BlockContainer implements ICableBlock {
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;

    public ManagerBlock() {
        super(BlockBehaviour.Properties
                      .of(Material.PISTON)
                      .destroyTime(2)
                      .sound(SoundType.METAL));
        registerDefaultState(getStateDefinition().any().setValue(TRIGGERED, false));
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new ManagerBlockEntity();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(
            BlockState state,
            Level level,
            @NotStored BlockPos pos,
            Block block,
            @NotStored BlockPos neighbourPos,
            boolean movedByPiston
    ) {
        if (!(level.getBlockEntity(pos) instanceof ManagerBlockEntity mgr)) return;
        if (!(level instanceof ServerLevel)) return;
        { // check redstone for triggers
            var isPowered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
            var debounce = state.getValue(TRIGGERED);
            if (isPowered && !debounce) {
                mgr.trackRedstonePulseUnprocessed();
                level.setBlock(pos, state.setValue(TRIGGERED, true), 4);
            } else if (!isPowered && debounce) {
                level.setBlock(pos, state.setValue(TRIGGERED, false), 4);
            }
        }
    }

    @Override
    public BlockEntity newBlockEntity(
            @Stored BlockPos pos,
            BlockState state
    ) {
        //noinspection DataFlowIssue
        return SFMBlockEntities.MANAGER_BLOCK_ENTITY.get().create(pos, state);
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
        if (level.getBlockEntity(pos) instanceof ManagerBlockEntity manager
            && player instanceof ServerPlayer serverPlayer) {
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
            NetworkHooks.openScreen(serverPlayer, manager, buf -> ManagerContainerMenu.encode(manager, buf));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, SFMBlockEntities.MANAGER_BLOCK_ENTITY.get(), ManagerBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(
            BlockState state,
            Level world,
            @NotStored BlockPos pos,
            BlockState oldState,
            boolean isMoving
    ) {
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TRIGGERED);
    }
}
