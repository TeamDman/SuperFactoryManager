package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.block_network.ICableBlock;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import com.mojang.serialization.MapCodec;
{% endcase %}
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
{% when '1.21', '1.21.1' %}
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.world.InteractionResult;
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import net.minecraft.world.level.material.Material;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.level.redstone.Orientation;
{% endcase %}
import net.minecraft.world.phys.BlockHitResult;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.network.NetworkHooks;
{% when '1.20.2' %}
import net.neoforged.neoforge.network.NetworkHooks;
{% when '1.20.3' %}
import net.neoforged.neoforge.network.NetworkHooks;
import org.apache.commons.lang3.NotImplementedException;
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import org.apache.commons.lang3.NotImplementedException;
{% endcase %}
import org.jetbrains.annotations.Nullable;

public class ManagerBlock extends BaseEntityBlock implements EntityBlock, ICableBlock {
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.MANAGER.get().getDescriptionId(),
            () -> "Factory Manager"
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    public ManagerBlock() {

        super(BlockBehaviour.Properties
                      .of(Material.PISTON)
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public ManagerBlock() {

        super(BlockBehaviour.Properties
                      .of()
{% when '26.1.2' %}
    public ManagerBlock(BlockBehaviour.Properties properties) {

        super(properties
{% endcase %}
                      .destroyTime(2)
                      .sound(SoundType.METAL));
        registerDefaultState(getStateDefinition().any().setValue(TRIGGERED, false));
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {

        return RenderShape.MODEL;
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.4', '1.21', '1.21.1' %}
    @Override
{% when '1.20.3' %}
    @Override
    protected MapCodec<WaterTankBlock> codec() {

        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

    @Override
{% when '26.1.2' %}
{% endcase %}
    @SuppressWarnings("deprecation")
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    @Override
{% endcase %}
    public void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block block,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            BlockPos neighbourPos,
            boolean movedByPiston
{% when '26.1.2' %}
            @Nullable Orientation orientation,
            boolean isMoving
{% endcase %}
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
    public @Nullable BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {

        return SFMBlockEntities.MANAGER.get().create(pos, state);
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
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

        if (level.getBlockEntity(pos) instanceof ManagerBlockEntity manager
            && player instanceof ServerPlayer serverPlayer) {
            // update warnings on disk as we open the gui
            DiskItem.rebuildWarnings(manager);
            openMenu(serverPlayer, manager);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {

        if (level.isClientSide()) return null;
        return createTickerHelper(type, SFMBlockEntities.MANAGER.get(), ManagerBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(
            BlockState state,
            Level world,
            BlockPos pos,
            BlockState oldState,
            boolean isMoving
    ) {

        CableNetworkManager.onCablePlaced(world, pos);
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @SuppressWarnings("deprecation")
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
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
{% when '26.1.2' %}
    protected void affectNeighborsAfterRemoval(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            boolean movedByPiston
    ) {

        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        level.updateNeighbourForOutputSignal(pos, this);
        CableNetworkManager.onCableRemoved(level, pos);
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
{% when '1.20.4' %}
    @Override
    protected MapCodec<WaterTankBlock> codec() {

        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

{% when '1.21', '1.21.1', '26.1.2' %}
    @Override
    protected MapCodec<WaterTankBlock> codec() {

        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState pState,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult pHitResult
    ) {

        if (level.getBlockEntity(pos) instanceof ManagerBlockEntity manager
            && player instanceof ServerPlayer serverPlayer) {
            // update warnings on disk as we open the gui
            DiskItem.rebuildWarnings(manager);
            openMenu(serverPlayer, manager);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

{% endcase %}
    @MCVersionDependentBehaviour
    private void openMenu(
            ServerPlayer player,
            ManagerBlockEntity manager
    ) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
        NetworkHooks.openScreen(player, manager, buf -> ManagerContainerMenu.encode(manager, buf));
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        player.openMenu(manager, buf -> ManagerContainerMenu.encode(manager, buf));
{% endcase %}
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {

        builder.add(TRIGGERED);
    }

}
