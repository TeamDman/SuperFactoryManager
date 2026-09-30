package ca.teamdman.sfm.common.capability.energystorage;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.energy.IEnergyStorage;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.energy.IEnergyStorage;
{% endcase %}

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
