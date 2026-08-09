package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

public class ForgeEnergyResourceType extends IntegerResourceType<IEnergyStorage> {
    public ForgeEnergyResourceType() {
        super(
                SFMWellKnownCapabilities.ENERGY,
                SFMResourceLocation.fromNamespaceAndPath("forge", "energy")
        );
    }

    @Override
    public Integer extract(
            IEnergyStorage iEnergyStorage,
            int slot,
            long amount,
            boolean simulate
    ) {
        int finalAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        return iEnergyStorage.extractEnergy(finalAmount, simulate);
    }

    @Override
    public boolean canExtract(IEnergyStorage capability, int slot) {
        return capability.canExtract();
    }

    @Override
    public int getSlots(IEnergyStorage handler) {
        return 1;
    }

    @Override
    public Integer insert(
            IEnergyStorage iEnergyStorage,
            int slot,
            Integer stack,
            boolean simulate
    ) {
        int accepted = iEnergyStorage.receiveEnergy(stack, simulate);
        return stack - accepted;
    }

    @Override
    public boolean canInsert(IEnergyStorage capability, int slot) {
        return capability.canReceive();
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof IEnergyStorage;
    }

    @Override
    public long getMaxStackSizeForSlot(
            IEnergyStorage iEnergyStorage,
            int slot
    ) {
        int maxStackSize = iEnergyStorage.getMaxEnergyStored();
        if (maxStackSize == Integer.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return maxStackSize;
    }

    @Override
    public IEnergyStorage createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new EnergyStorage(contents.tier.getIntScalarMaxStackSize()) {
            @Override
            public boolean canReceive() {
                boolean isValid = this.energy > 0 || contents.isEmpty();
                if (isValid) {
                    contents.lastUsedResource = BufferBlock.ContainedResource.Energy;
                }
                return isValid;
            }
        };
    }

    @Override
    public Integer getStackInSlot(
            IEnergyStorage iEnergyStorage,
            int slot
    ) {
        return iEnergyStorage.getEnergyStored();
    }
}
