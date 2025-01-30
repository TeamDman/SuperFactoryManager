package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.cablenetwork.ICable;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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

import javax.annotation.Nullable;

public class ManagerBlock extends BaseEntityBlock implements EntityBlock, ICable {
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;

    public ManagerBlock() {
        super(BlockBehaviour.Properties
                      .of(Material.PISTON)
                      .destroyTime(2)
                      .sound(SoundType.METAL));
        registerDefaultState(getStateDefinition().any().setValue(TRIGGERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TRIGGERED);
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }


    @Override
    public void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block block,
            BlockPos neighbourPos,
            boolean movedByPiston
    ) {
        if (!(level.getBlockEntity(pos) instanceof ManagerBlockEntity mgr)) return;
        var isPowered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
        var debounce  = state.getValue(TRIGGERED);
        if (isPowered && !debounce) {
            mgr.trackRedstonePulseUnprocessed();
            level.setBlock(pos, state.setValue(TRIGGERED, true), 4);
        } else if (!isPowered && debounce) {
            level.setBlock(pos, state.setValue(TRIGGERED, false), 4);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return SFMBlockEntities.MANAGER_BLOCK_ENTITY
                .get()
                .create(pos, state);
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (level.getBlockEntity(pos) instanceof ManagerBlockEntity tile && player instanceof ServerPlayer sp) {
            NetworkHooks.openGui(sp, tile, buf -> {
                buf.writeBlockPos(tile.getBlockPos());
                buf.writeUtf(tile.getProgram().orElse(""), Program.MAX_PROGRAM_LENGTH);
            });
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, SFMBlockEntities.MANAGER_BLOCK_ENTITY.get(), ManagerBlockEntity::serverTick);
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader world, BlockPos pos, BlockPos neighbor) {
        if (world instanceof ServerLevel) {
            CableNetworkManager
                    .getOrRegisterNetwork(((Level) world), pos)
                    .ifPresent(network -> network.rebuildAdjacentInventories(pos));
        }
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        CableNetworkManager.getOrRegisterNetwork(world, pos);
        CableNetworkManager.printDebugInfo();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                Containers.dropContents(level, pos, container);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            CableNetworkManager.unregister(level, pos);
            CableNetworkManager.printDebugInfo();
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
