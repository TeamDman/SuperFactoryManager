package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntity;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;


/// Version-agnostic way to retrieve the contents of a {@link BufferBlockEntity}.
public class BufferBlockCapabilityProvider implements SFMBlockCapabilityProvider<Object> {

    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return capabilityKind.getResourceType() != null;
    }

    @Override
    public SFMBlockCapabilityResult<Object> getCapability(
            SFMBlockCapabilityKind<Object> capabilityKind,
            World level,
            BlockPos pos,
            IBlockState state,
            @Nullable TileEntity blockEntity,
            @Nullable EnumFacing direction
    ) {
        if (!(blockEntity instanceof BufferBlockEntity bufferBlockEntity)) return SFMBlockCapabilityResult.empty();
        ResourceType<?, ?, ?> resourceType = capabilityKind.getResourceType();
        if (resourceType == null) return SFMBlockCapabilityResult.empty();
        //noinspection unchecked
        return (SFMBlockCapabilityResult<Object>) bufferBlockEntity.getContents().getCapability(resourceType);
    }
}