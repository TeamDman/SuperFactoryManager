package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.block_network.WaterNetworkManager;
import ca.teamdman.sfm.common.blockentity.WaterTankBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDirections;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@SuppressWarnings("deprecation")

public class WaterTankBlock extends BaseEntityBlock implements EntityBlock, BucketPickup, LiquidBlockContainer {
    public static final BooleanProperty IN_WATER = BooleanProperty.create("in_water");

    @SFMLocalizationDatagen
    public static final LocalizationEntry WATER_TANK_ITEM_TOOLTIP_1 = new LocalizationEntry(
            () -> SFMBlocks.WATER_TANK.get().getDescriptionId() + ".tooltip.1",
            () -> "Requires two adjacent water sources."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry WATER_TANK_ITEM_TOOLTIP_2 = new LocalizationEntry(
            () -> SFMBlocks.WATER_TANK.get().getDescriptionId() + ".tooltip.2",
            () -> "More effective when also adjacent to other active water tanks."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry WATER_TANK_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.WATER_TANK.get().getDescriptionId(),
            () -> "Water Tank"
    );


    public WaterTankBlock() {

        super(BlockBehaviour.Properties.of().destroyTime(2).sound(SoundType.WOOD));
        registerDefaultState(getStateDefinition().any().setValue(IN_WATER, false));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            BlockState pOldState,
            boolean pIsMoving
    ) {
        /// Do nothing because the {@link WaterTankBlockEntity#onLoad()} method handles this logic.
        /// Note that the timing of {@link WaterTankBlockEntity#onLoad()} is different as of 1.20.2.
        /// See {@link net.minecraft.world.level.chunk.LevelChunk#addAndRegisterBlockEntity(BlockEntity)}.
        /// For <1.20.2: onLoad is called immediately.
        /// For >=1.20.2: onLoad is deferred to the next block entity tick.
        /// In practice, this just affects the timing of how game tests should expect changes to be reflected in the {@link WaterTankBlockEntity#TANK} capacity.
//        WaterNetworkManager.onWaterTankBlockActiveStateChanged(pLevel, pPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            BlockState pNewState,
            boolean pIsMoving
    ) {

        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);

        // Changing the active block state causes this to fire, we want to debounce this
        // so that the neighbour method does a single update for remove+place when changing active state
        if (!pNewState.is(pState.getBlock())) {
            WaterNetworkManager.onWaterTankBlockRemoved(pLevel, pPos);
        }
    }

    @Override
    public void appendHoverText(
            ItemStack pStack,
            @Nullable BlockGetter pLevel,
            List<Component> pTooltip,
            TooltipFlag pFlag
    ) {

        pTooltip.add(WATER_TANK_ITEM_TOOLTIP_1
                             .getComponent()
                             .withStyle(ChatFormatting.GRAY));
        pTooltip.add(WATER_TANK_ITEM_TOOLTIP_2
                             .getComponent()
                             .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {

        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {

        return SFMBlockEntities.WATER_TANK.get().create(pos, state);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {

        return defaultBlockState().setValue(
                IN_WATER,
                hasWaterNeighbours(context.getLevel(), context.getClickedPos())
        );
    }

    public boolean hasWaterNeighbours(
            LevelAccessor level,
            BlockPos pos
    ) {

        int neighbourWaterCount = 0;
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            target.set(pos).move(direction);
            FluidState state = level.getFluidState(target);
            if (state.isSource() && state.is(FluidTags.WATER)) {
                if (++neighbourWaterCount == 2) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block blockIn,
            BlockPos fromPos,
            boolean isMoving
    ) {

        if (level.isClientSide) return;
        boolean isActive = hasWaterNeighbours(level, pos);
        if (state.getValue(IN_WATER) != isActive) {
            BlockState newState = defaultBlockState().setValue(IN_WATER, isActive);
            level.setBlock(
                    pos,
                    newState,
                    Block.UPDATE_ALL
            );
            WaterNetworkManager.onWaterTankBlockActiveStateChanged(level, pos);
        }
    }

    @Override
    public ItemStack pickupBlock(
            @Nullable Player player,
            LevelAccessor levelAccessor,
            BlockPos blockPos,
            BlockState blockState
    ) {

        return blockState.getValue(IN_WATER) ? new ItemStack(Fluids.WATER.getBucket()) : ItemStack.EMPTY;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {

        return Fluids.WATER.getPickupSound();
    }

    @Override
    public boolean canPlaceLiquid(
            @Nullable Player player,
            BlockGetter blockGetter,
            BlockPos blockPos,
            BlockState blockState,
            Fluid fluid
    ) {

        return fluid.isSame(Fluids.WATER);
    }

    @Override
    public boolean placeLiquid(
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            FluidState fluid
    ) {

        return fluid.getType().isSame(Fluids.WATER);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {

        builder.add(IN_WATER);
    }

}
