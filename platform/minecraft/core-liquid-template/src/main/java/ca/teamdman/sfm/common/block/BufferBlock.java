package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntity;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
{% if features.redstone_buffer_storage %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endif %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1", "26.1.2" %}
import com.mojang.serialization.MapCodec;
{% else %}
{% endcase %}
import net.minecraft.core.BlockPos;
{% if features.redstone_buffer_storage %}
import net.minecraft.util.Mth;
{% endif %}
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1", "26.1.2" %}
import org.apache.commons.lang3.NotImplementedException;
{% else %}
{% endcase %}
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class BufferBlock extends BaseEntityBlock {
    public static final EnumProperty<ContainedResource> CONTAINED_RESOURCE = EnumProperty.create(
            "resource",
            ContainedResource.class
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry BUFFER_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.BUFFER_BLOCK.get().getDescriptionId(),
            () -> "Resource Buffer"
    );

    public final BufferBlockTier tier;

{% if features.redstone_buffer_storage %}
    @SuppressWarnings("deprecation")
    @MCVersionDependentBehaviour
    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @MCVersionDependentBehaviour
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BufferBlockEntity buffer
               ? Mth.clamp(buffer.getContents().getStoredRedstone(), 0, 15)
               : 0;
    }

    @SuppressWarnings("deprecation")
    @MCVersionDependentBehaviour
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        super.onRemove(state, level, pos, next, moved);
        if (!state.is(next.getBlock())) {
            level.updateNeighbourForOutputSignal(pos, this);
        }
    }

{% endif %}
    public BufferBlock(
            Properties pProperties,
            BufferBlockTier tier
    ) {

        super(pProperties);
        registerDefaultState(getStateDefinition().any().setValue(CONTAINED_RESOURCE, ContainedResource.Item));
        this.tier = tier;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos pPos,
            BlockState pState
    ) {

        return SFMBlockEntities.BUFFER.get().create(pPos, pState);
    }

{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1", "26.1.2" %}
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

{% else %}
{% endcase %}
    @SuppressWarnings("deprecation")
    @Override
    public RenderShape getRenderShape(BlockState pState) {

        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext pContext) {

        return defaultBlockState().setValue(CONTAINED_RESOURCE, ContainedResource.Unknown);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level pLevel,
            BlockState pState,
            BlockEntityType<T> pBlockEntityType
    ) {

        if (pLevel.isClientSide()) return null;
        return createTickerHelper(
                pBlockEntityType,
                SFMBlockEntities.BUFFER.get(),
                BufferBlockEntity::serverTick
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {

        pBuilder.add(CONTAINED_RESOURCE);
    }

    public enum ContainedResource implements StringRepresentable {
        Item,
        Fluid,
        Energy,
        Chemical,
        Redstone,
{% if features.image_resources %}
        Image,
{% endif %}
        Unknown;

        @Override
        public String getSerializedName() {

            return switch (this) {
                case Item -> "item";
                case Fluid -> "fluid";
                case Energy -> "energy";
                case Chemical -> "chemical";
                case Redstone -> "redstone";
{% if features.image_resources %}
                case Image -> "image";
{% endif %}
                case Unknown -> "unknown";
            };
        }

        public static ContainedResource from(ResourceType<?, ?, ?> resourceType) {

            String name = Objects.requireNonNull(SFMResourceTypes.registry().getId(resourceType)).getPath();
            if (name.equals("item")) {
                return Item;
            } else if (name.equals("fluid")) {
                return Fluid;
            } else if (name.equals("forge_energy")) {
                return Energy;
            } else if (name.equals("redstone")) {
                return Redstone;
{% if features.image_resources %}
            } else if (name.equals("image")) {
                return Image;
{% endif %}
            } else if (SFMModCompat.isMekanismLoaded()) {
                if (name.equals("gas") || name.equals("infusion") || name.equals("pigment") || name.equals("slurry")) {
                    return Chemical;
                } else if (name.equals("mekanism_energy")) {
                    return Energy;
                }
            }
            return Unknown;
        }
    }

}
