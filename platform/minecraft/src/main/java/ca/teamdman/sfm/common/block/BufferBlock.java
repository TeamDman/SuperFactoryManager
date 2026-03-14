package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntity;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import net.minecraft.core.BlockPos;
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
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class BufferBlock extends BaseEntityBlock {
    public static final EnumProperty<ContainedResource> CONTAINED_RESOURCE = EnumProperty.create(
            "resource",
            ContainedResource.class
    );

    public final BufferBlockTier tier;

    public BufferBlock(Properties pProperties, BufferBlockTier tier) {
        super(pProperties);
        registerDefaultState(getStateDefinition().any().setValue(CONTAINED_RESOURCE, ContainedResource.Item));
        this.tier = tier;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos pPos,
            BlockState pState
    ) {
        return SFMBlockEntities.BUFFER_BLOCK_ENTITY.get().create(pPos, pState);
    }

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
                SFMBlockEntities.BUFFER_BLOCK_ENTITY.get(),
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
        Unknown;

        @Override
        public String getSerializedName() {
            return switch (this) {
                case Item -> "item";
                case Fluid -> "fluid";
                case Energy -> "energy";
                case Chemical -> "chemical";
                case Redstone -> "redstone";
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
