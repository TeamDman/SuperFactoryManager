package ca.teamdman.sfm.common.resourcetype;

{% if features.redstone_buffer_storage %}
{% else %}
import ca.teamdman.sfm.common.block.BufferBlock;
{% endif %}
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
{% if features.redstone_buffer_storage %}
                return contents.allowInsertion(RedstoneResourceType.this);
{% else %}
                boolean isValid = this.getStoredAmount() > 0 || contents.isEmpty();
                if (isValid) {
                    contents.lastUsedResource = BufferBlock.ContainedResource.Redstone;
                }
                return isValid;
{% endif %}
            }
{% if features.redstone_buffer_storage %}

            @Override
            protected void onContentsChanged() {
                contents.onRedstoneChanged();
            }
{% endif %}
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
{% if features.redstone_buffer_storage %}
        int requested = (int) Math.max(0L, Math.min(amount, Integer.MAX_VALUE));
        return redstoneCapability.extract(requested, simulate);
{% else %}
        return 0;
{% endif %}
    }
{% if features.redstone_buffer_storage %}

    @Override
    public boolean canExtract(IRedstoneSignalStorage capability, int slot) {
        return capability.canExtract();
    }

    @Override
    public boolean canInsert(IRedstoneSignalStorage capability, int slot) {
        return capability.canReceive();
    }
{% endif %}

    @Override
    public int getSlots(IRedstoneSignalStorage handler) {
        return 1;
    }

    @Override
    public long getMaxStackSizeForSlot(
            IRedstoneSignalStorage redstoneCapability,
            int slot
    ) {
{% if features.redstone_buffer_storage %}
        return redstoneCapability.getMaxStoredAmount();
{% else %}
        return 15;
{% endif %}
    }

    @Override
    public Integer insert(
            IRedstoneSignalStorage redstoneCapability,
            int slot,
            Integer integer,
            boolean simulate
    ) {
{% if features.redstone_buffer_storage %}
        return integer - redstoneCapability.insert(integer, simulate);
{% else %}
        return 0;
{% endif %}
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof IRedstoneSignalStorage;
    }
}
