package ca.teamdman.sfm.common.capability;

import net.minecraft.nbt.IntTag;
import net.minecraft.util.Mth;
import net.minecraftforge.common.util.INBTSerializable;

/// A container for storing "redstone units", which CAN exceed 15.
public class RedstoneSignalStorage implements IRedstoneSignalStorage, INBTSerializable<IntTag> {
    private int value;
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
        if (!simulate && accept > 0) {
            this.value += accept;
            onContentsChanged();
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
        if (!simulate && extract > 0) {
            this.value -= extract;
            onContentsChanged();
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

    protected void onContentsChanged() {
    }

    @Override
    public void deserializeNBT(IntTag nbt) {
        this.value = Mth.clamp(nbt.getAsInt(), 0, this.maxValue);
    }

    @Override
    public IntTag serializeNBT() {
        return IntTag.valueOf(this.value);
    }
}
