package ca.teamdman.sfm.common.capability.energystorage;

import net.minecraftforge.energy.IEnergyStorage;

public record EnergyAcceptorEnergyStorageWrapper(
        IEnergyStorage inner
) implements IEnergyStorage {
    @Override
    public int receiveEnergy(
            int maxReceive,
            boolean simulate
    ) {
        return inner.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(
            int maxExtract,
            boolean simulate
    ) {
        return inner.extractEnergy(maxExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return inner.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        // #322: AE always reports zero, we want SFM to be able to insert energy
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canExtract() {
        return inner.canExtract();
    }

    @Override
    public boolean canReceive() {
        return inner.canReceive();
    }
}
