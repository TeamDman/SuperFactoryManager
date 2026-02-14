package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.util.Mth;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.common.util.INBTSerializable;

/// A container for storing "redstone units", which CAN exceed 15.
public class RedstoneSignalStorage implements IRedstoneSignalStorage, INBTSerializable<NBTTagInt> {
    public int value = 0;
    private final int maxValue;

    public RedstoneSignalStorage(int signal, int maxValue) {
        this.maxValue = Mth.clamp(maxValue, 0, Integer.MAX_VALUE);
        this.value = Mth.clamp(signal, 0, this.maxValue);
    }

    @Override
    public int insert(
            int amount,
            boolean simulate
    ) {
        if (!this.canReceive()) {
            return 0; // accept nothing
        }
        int accept = Mth.clamp(amount, 0, this.maxValue - this.value);
        if (!simulate) {
            this.value += accept;
        }
        return accept;
    }

    @Override
    public int extract(
            int amount,
            boolean simulate
    ) {
        if (!this.canExtract()) {
            return 0; // extract nothing
        }
        int extract = Mth.clamp(amount, 0, this.value);
        if (!simulate) {
            this.value -= extract;
        }
        return extract;
    }

    @Override
    public int getStoredAmount() {
        return this.value;
    }

    @Override
    public int getMaxStoredAmount() {
        return this.maxValue;
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    @Override
    public void setStoredAmount(int amount) {
        this.value = Mth.clamp(amount, 0, this.maxValue);
    }

    @Override
    public void deserializeNBT(NBTTagInt nbt) {
        this.value = nbt.getInt();
    }

    @Override
    public NBTTagInt serializeNBT() {
        return new NBTTagInt(this.value);
    }
}