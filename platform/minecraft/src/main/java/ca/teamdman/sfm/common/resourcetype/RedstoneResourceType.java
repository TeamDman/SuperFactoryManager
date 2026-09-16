package ca.teamdman.sfm.common.resourcetype;

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
                return contents.allowInsertion(RedstoneResourceType.this);
            }

            @Override
            protected void onContentsChanged() {
                contents.onRedstoneChanged();
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
        int requested = (int) Math.max(0L, Math.min(amount, Integer.MAX_VALUE));
        return redstoneCapability.extract(requested, simulate);
    }

    @Override
    public boolean canExtract(IRedstoneSignalStorage capability, int slot) {
        return capability.canExtract();
    }

    @Override
    public boolean canInsert(IRedstoneSignalStorage capability, int slot) {
        return capability.canReceive();
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
        return redstoneCapability.getMaxStoredAmount();
    }

    @Override
    public Integer insert(
            IRedstoneSignalStorage redstoneCapability,
            int slot,
            Integer integer,
            boolean simulate
    ) {
        return integer - redstoneCapability.insert(integer, simulate);
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof IRedstoneSignalStorage;
    }
}
