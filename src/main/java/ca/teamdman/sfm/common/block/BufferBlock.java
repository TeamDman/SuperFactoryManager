package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntity;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IStringSerializable;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class BufferBlock extends Block implements ITileEntityProvider {
    public static final PropertyEnum<ContainedResource> CONTAINED_RESOURCE = PropertyEnum.create(
            "resource",
            ContainedResource.class
    );

    public final BufferBlockTier tier;

    public BufferBlock(BufferBlockTier tier) {
        super(net.minecraft.block.material.Material.IRON);
        this.tier = tier;
        setDefaultState(this.blockState.getBaseState().withProperty(CONTAINED_RESOURCE, ContainedResource.Item));
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(CONTAINED_RESOURCE, ContainedResource.values()[meta]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(CONTAINED_RESOURCE).ordinal();
    }

    @NotNull
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, CONTAINED_RESOURCE);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(@NotNull World worldIn, int meta) {
        return new BufferBlockEntity(tier);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.MODEL;
    }


    public enum ContainedResource implements IStringSerializable {
        Item,
        Fluid,
        Energy,
        Chemical,
        Redstone,
        Unknown;



        public static ContainedResource from(ResourceType<?, ?, ?> resourceType) {
            String name = Objects.requireNonNull(SFMResourceTypes.registry().getId(resourceType.container)).getPath();
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

        @Override
        public String getName() {
            return switch (this) {
                case Item -> "item";
                case Fluid -> "fluid";
                case Energy -> "energy";
                case Chemical -> "chemical";
                case Redstone -> "redstone";
                case Unknown -> "unknown";
            };
        }
    }
}
