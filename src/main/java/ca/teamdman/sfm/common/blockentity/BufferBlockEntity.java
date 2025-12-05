package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.BufferBlockTier;
import ca.teamdman.sfm.common.capability.BufferBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class BufferBlockEntity extends TileEntity implements ITickable {
    private final BufferBlockEntityContents contents;

    public BufferBlockEntity(BufferBlockTier tier) {
        this.contents = new BufferBlockEntityContents(tier);
    }

    public BufferBlockEntity() {
        this(BufferBlockTier.Unit);
    }

    @Override
    public boolean hasCapability(@NotNull Capability<?> cap, @Nullable EnumFacing side) {
        SFMBlockCapabilityKind<?> capKind = new SFMBlockCapabilityKind<>(() -> cap);
        BufferBlockCapabilityProvider bufferBlockCapabilityProvider = new BufferBlockCapabilityProvider();
        SFMBlockCapabilityResult<?> found = bufferBlockCapabilityProvider.getCapability(
                (SFMBlockCapabilityKind<Object>) capKind,
                world,
                getPos(),
                world.getBlockState(getPos()),
                this,
                side
        );
        if (found.isPresent()) {
            return true;
        }
        return super.hasCapability(cap, side);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(
            @NotNull Capability<T> cap,
            @Nullable EnumFacing side
    ) {
        SFMBlockCapabilityKind<T> capKind = new SFMBlockCapabilityKind<>(() -> cap);
        BufferBlockCapabilityProvider bufferBlockCapabilityProvider = new BufferBlockCapabilityProvider();
        SFMBlockCapabilityResult<T> found = (SFMBlockCapabilityResult<T>) bufferBlockCapabilityProvider.getCapability(
                (SFMBlockCapabilityKind<Object>) capKind,
                world,
                getPos(),
                world.getBlockState(getPos()),
                this,
                side
        );
        if (found.isPresent()) {
            return (T) found.inner();
        } else {
            return super.getCapability(cap, side);
        }
    }

    public BufferBlockEntityContents getContents() {
        return contents;
    }

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }
        if (getContents().lastUsedResource != world.getBlockState(getPos()).getValue(BufferBlock.CONTAINED_RESOURCE)) {
            world.setBlockState(
                    getPos(),
                    world.getBlockState(getPos()).withProperty(
                            BufferBlock.CONTAINED_RESOURCE,
                            getContents().lastUsedResource
                    ),
                    3
            );
        }
    }
}
