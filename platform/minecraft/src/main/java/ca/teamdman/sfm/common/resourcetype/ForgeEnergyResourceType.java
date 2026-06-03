package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

public class ForgeEnergyResourceType extends IntegerResourceType<EnergyHandler> {
    public ForgeEnergyResourceType() {
        super(
                SFMWellKnownCapabilities.ENERGY,
                SFMResourceLocation.fromNamespaceAndPath("forge", "energy")
        );
    }

    @Override
    public Integer extract(
            EnergyHandler _handler,
            int slot,
            long amount,
            boolean simulate
    ) {
        IEnergyStorage handler = IEnergyStorage.of(_handler);
        int finalAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        return handler.extractEnergy(finalAmount, simulate);
    }

    @Override
    public boolean canExtract(EnergyHandler _handler, int slot) {
        return IEnergyStorage.of(_handler).canExtract();
    }

    @Override
    public int getSlots(EnergyHandler _handler) {
        return 1;
    }

    @Override
    public Integer insert(
            EnergyHandler _handler,
            int slot,
            Integer stack,
            boolean simulate
    ) {
        int accepted = IEnergyStorage.of(_handler).receiveEnergy(stack, simulate);
        return stack - accepted;
    }

    @Override
    public boolean canInsert(EnergyHandler _handler, int slot) {
        return IEnergyStorage.of(_handler).canReceive();
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof EnergyHandler;
    }

    @Override
    public long getMaxStackSizeForSlot(
            EnergyHandler _handler,
            int slot
    ) {
        int maxStackSize = IEnergyStorage.of(_handler).getMaxEnergyStored();
        if (maxStackSize == Integer.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return maxStackSize;
    }

    @Override
    public EnergyHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new SimpleEnergyHandler(contents.tier.getIntScalarMaxStackSize()) {

/*            @Override
            public boolean canReceive() {
                boolean isValid = this.energy > 0 || contents.isEmpty();
                if (isValid) {
                    contents.lastUsedResource = BufferBlock.ContainedResource.Energy;
                }
                return isValid;
            }*/
        };
    }

    @Override
    public Integer getStackInSlot(
            EnergyHandler _handler,
            int slot
    ) {
        return IEnergyStorage.of(_handler).getEnergyStored();
    }
}
