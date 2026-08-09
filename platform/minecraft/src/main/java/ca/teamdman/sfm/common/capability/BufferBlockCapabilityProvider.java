package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntity;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction direction
    ) {
        if (!(blockEntity instanceof BufferBlockEntity bufferBlockEntity)) return SFMBlockCapabilityResult.empty();
        ResourceType<?, ?, ?> resourceType = capabilityKind.getResourceType();
        if (resourceType == null) return SFMBlockCapabilityResult.empty();
        //noinspection unchecked
        return (SFMBlockCapabilityResult<Object>) bufferBlockEntity.getContents().getCapability(resourceType);
    }
}