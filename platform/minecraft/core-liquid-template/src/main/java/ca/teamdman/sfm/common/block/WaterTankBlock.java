package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.block_network.WaterNetworkManager;
import ca.teamdman.sfm.common.blockentity.WaterTankBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDirections;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import com.mojang.serialization.MapCodec;
{% endcase %}
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.core.component.DataComponentGetter;
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.server.level.ServerLevel;
{% endcase %}
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
{% when '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.world.entity.player.Player;
{% when '1.21', '1.21.1' %}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
{% when '26.1.2' %}
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
{% endcase %}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.item.component.TooltipProvider;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import net.minecraft.world.level.material.Material;
{% when '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import org.apache.commons.lang3.NotImplementedException;
{% when '26.1.2' %}
import net.minecraft.world.level.redstone.Orientation;
import org.apache.commons.lang3.NotImplementedException;
{% endcase %}
import org.jetbrains.annotations.Nullable;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import java.util.List;
{% when '26.1.2' %}
{% endcase %}
import java.util.Optional;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import java.util.function.Consumer;
{% endcase %}

@SuppressWarnings("deprecation")

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public class WaterTankBlock extends BaseEntityBlock implements EntityBlock, BucketPickup, LiquidBlockContainer {
{% when '26.1.2' %}
public class WaterTankBlock extends BaseEntityBlock implements EntityBlock, BucketPickup, LiquidBlockContainer, TooltipProvider {
{% endcase %}
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


{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public WaterTankBlock() {
{% when '26.1.2' %}
    public WaterTankBlock(BlockBehaviour.Properties properties) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        super(BlockBehaviour.Properties.of(Material.PISTON).destroyTime(2).sound(SoundType.WOOD));
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super(BlockBehaviour.Properties.of().destroyTime(2).sound(SoundType.WOOD));
{% when '26.1.2' %}
        super(properties.destroyTime(2).sound(SoundType.WOOD));
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
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
{% when '26.1.2' %}
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel pLevel, BlockPos pPos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, pLevel, pPos, movedByPiston);
        WaterNetworkManager.onWaterTankBlockRemoved(pLevel, pPos);
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
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

{% when '1.20.3', '1.20.4' %}
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
    protected MapCodec<WaterTankBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

{% when '1.21', '1.21.1' %}

    @Override
    public void appendHoverText(
            ItemStack pStack,
            Item.TooltipContext pContext,
            List<Component> pTootipComponents,
            TooltipFlag pTooltipFlag
    ) {

        pTootipComponents.add(WATER_TANK_ITEM_TOOLTIP_1
                             .getComponent()
                             .withStyle(ChatFormatting.GRAY));
        pTootipComponents.add(WATER_TANK_ITEM_TOOLTIP_2
                             .getComponent()
                             .withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected MapCodec<WaterTankBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

{% when '26.1.2' %}

    @Override
    protected MapCodec<WaterTankBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @SuppressWarnings("deprecation")
{% when '26.1.2' %}
{% endcase %}
    public void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            Block blockIn,
            BlockPos fromPos,
{% when '26.1.2' %}
            Block block,
            @Nullable Orientation orientation,
{% endcase %}
            boolean isMoving
    ) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (level.isClientSide) return;
{% when '26.1.2' %}
        if (level.isClientSide()) return;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            LevelAccessor level,
            BlockPos pos,
            BlockState state
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            @Nullable Player player,
            LevelAccessor levelAccessor,
            BlockPos blockPos,
            BlockState blockState
{% when '26.1.2' %}
            @Nullable LivingEntity player,
            LevelAccessor levelAccessor,
            BlockPos blockPos,
            BlockState blockState
{% endcase %}
    ) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        return state.getValue(IN_WATER) ? new ItemStack(Fluids.WATER.getBucket()) : ItemStack.EMPTY;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return blockState.getValue(IN_WATER) ? new ItemStack(Fluids.WATER.getBucket()) : ItemStack.EMPTY;
{% endcase %}
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {

        return Fluids.WATER.getPickupSound();
    }

    @Override
    public boolean canPlaceLiquid(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            BlockGetter level,
            BlockPos pos,
            BlockState state,
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            @Nullable Player player,
            BlockGetter blockGetter,
            BlockPos blockPos,
            BlockState blockState,
{% when '26.1.2' %}
            @Nullable LivingEntity player,
            BlockGetter blockGetter,
            BlockPos blockPos,
            BlockState blockState,
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        consumer.accept(
                WATER_TANK_ITEM_TOOLTIP_1.getComponent().withStyle(ChatFormatting.GRAY)
        );
        consumer.accept(
                WATER_TANK_ITEM_TOOLTIP_2.getComponent().withStyle(ChatFormatting.GRAY)
        );
    }
{% endcase %}
}
