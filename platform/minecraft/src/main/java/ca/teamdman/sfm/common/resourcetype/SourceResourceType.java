package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;

public class SourceResourceType extends IntegerResourceType<ISourceCap> {
    public static final SFMBlockCapabilityKind<ISourceCap> CAP = new SFMBlockCapabilityKind<>(
            CapabilityRegistry.SOURCE_CAPABILITY
    );

    public SourceResourceType() {
        super(CAP, SFMResourceLocation.fromNamespaceAndPath("ars_nouveau", "source"));
    }

    @Override
    public ISourceCap createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return null;
    }

    @Override
    public Integer getStackInSlot(ISourceCap iSourceCap, int slot) {
        return iSourceCap.getSource();
    }

    @Override
    public Integer extract(ISourceCap iSourceCap, int slot, long amount, boolean simulate) {
        int finalAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;

        return iSourceCap.extractSource(finalAmount, simulate);
    }

    @Override
    public int getSlots(ISourceCap handler) {
        return 1;
    }

    @Override
    public long getMaxStackSizeForSlot(ISourceCap iSourceCap, int slot) {
        return iSourceCap.getSourceCapacity();
    }

    @Override
    public Integer insert(ISourceCap iSourceCap, int slot, Integer stack, boolean simulate) {
        int accepted = iSourceCap.receiveSource(stack, simulate);
        return stack - accepted;
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof ISourceCap;
    }
}
