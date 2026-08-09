package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.block_network.ICableBlock;
import ca.teamdman.sfm.common.containermenu.LibraryContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * A block that stores SFML library definitions (protocols, structs, macros).
 * Can be referenced by manager programs via "use library" statements.
 * The front face shows disk slot indicators via a BlockEntityRenderer.
 */
public class LibraryBlock extends BaseEntityBlock implements EntityBlock, ICableBlock {

    @SFMLocalizationDatagen
    public static final LocalizationEntry LIBRARY_BLOCK = new LocalizationEntry(
            "block.sfm.library",
            "SFML Library Block"
    );

    /**
     * Property tracking which direction the front face is facing.
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public LibraryBlock() {
        super(BlockBehaviour.Properties
                .of(Material.PISTON)
                .destroyTime(1.5f)
                .sound(SoundType.METAL));
        registerDefaultState(getStateDefinition().any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return SFMBlockEntities.LIBRARY_BLOCK_ENTITY.get().create(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LibraryBlockEntity library) {
            NetworkHooks.openScreen(
                    (ServerPlayer) player,
                    library,
                    buf -> LibraryContainerMenu.encode(library, buf)
            );
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean isMoving
    ) {
        CableNetworkManager.onCablePlaced(level, pos);
        // Notify managers that a library block was added
        if (!level.isClientSide()) {
            CableNetworkManager.getNetworksForLevel(level)
                    .values().stream()
                    .filter(network -> network.isAdjacentToCable(pos))
                    .forEach(CableNetwork::invalidateAutoLabelsAndNotifyDependents);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean isMoving
    ) {
        if (!state.is(newState.getBlock())) {
            // Capture managers to notify BEFORE removal (while library still exists in cache)
            Set<BlockPos> managersToNotify = new HashSet<>();
            if (!level.isClientSide()) {
                CableNetworkManager.getNetworksForLevel(level)
                        .values().stream()
                        .filter(network -> network.isAdjacentToCable(pos))
                        .forEach(network -> {
                            for (BlockPos managerPos : network.getOrRebuildAutoLabels()
                                    .getPositions(ManagerBlockEntity.MANAGER_LABEL)
                                    .blockPosIterator()) {
                                managersToNotify.add(managerPos.immutable());
                            }
                            network.invalidateAutoLabelCache();
                        });
            }

            // Drop all disks when block is broken
            if (level.getBlockEntity(pos) instanceof LibraryBlockEntity library) {
                Containers.dropContents(level, pos, library);
            }
            CableNetworkManager.onCableRemoved(level, pos);
            super.onRemove(state, level, pos, newState, isMoving);

            // NOW notify managers (after library is fully removed)
            for (BlockPos managerPos : managersToNotify) {
                if (level.getBlockEntity(managerPos) instanceof ManagerBlockEntity manager) {
                    manager.rebuildProgramAndUpdateDisk();
                }
            }
        }
    }
}
