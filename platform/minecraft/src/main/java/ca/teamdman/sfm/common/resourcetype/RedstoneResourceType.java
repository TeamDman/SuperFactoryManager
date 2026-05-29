package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.IRedstoneSignalStorage;
import ca.teamdman.sfm.common.capability.RedstoneSignalStorage;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.util.SFMResourceLocation;

public class RedstoneResourceType extends IntegerResourceType<IRedstoneSignalStorage> {
    public RedstoneResourceType() {
        super(
                SFMWellKnownCapabilities.REDSTONE_HANDLER,
                SFMResourceLocation.fromNamespaceAndPath("minecraft", "redstone")
        );
    }

    @Override
    public IRedstoneSignalStorage createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new RedstoneSignalStorage(0, contents.tier.getIntScalarMaxStackSize()) {
            @Override
            public boolean canReceive() {
                boolean isValid = this.getStoredAmount() > 0 || contents.isEmpty();
                if (isValid) {
                    contents.lastUsedResource = BufferBlock.ContainedResource.Redstone;
                }
                return isValid;
            }
        };
    }

    @Override
    public Integer getStackInSlot(
            IRedstoneSignalStorage redstoneCapability,
            int slot
    ) {
        return redstoneCapability.getStoredAmount();
    }

    @Override
    public Integer extract(
            IRedstoneSignalStorage redstoneCapability,
            int slot,
            long amount,
            boolean simulate
    ) {
        return 0;
    }

    @Override
    public int getSlots(IRedstoneSignalStorage handler) {
        return 1;
    }

    @Override
    public long getMaxStackSizeForSlot(
            IRedstoneSignalStorage redstoneCapability,
            int slot
    ) {
        return 15;
    }

    @Override
    public Integer insert(
            IRedstoneSignalStorage redstoneCapability,
            int slot,
            Integer integer,
            boolean simulate
    ) {
        return 0;
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof IRedstoneSignalStorage;
    }
}
