package ca.teamdman.sfm.common.capability;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.IntTag;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

/// A container for storing "redstone units", which CAN exceed 15.
public class RedstoneSignalStorage implements IRedstoneSignalStorage, INBTSerializable<IntTag> {
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
    public @UnknownNullability IntTag serializeNBT(HolderLookup.Provider provider) {
        return IntTag.valueOf(this.value);
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            IntTag nbt
    ) {
        this.value = nbt.getAsInt();

    }
}