package ca.teamdman.sfm.common.capability;

import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/// A container for storing "redstone units", which CAN exceed 15.
public class RedstoneSignalStorage implements IRedstoneSignalStorage, ValueIOSerializable {
    public int value = 0;
    private final int maxValue;

    public RedstoneSignalStorage(int signal, int maxValue) {
        this.maxValue = Mth.clamp(maxValue, 0, Integer.MAX_VALUE);
        this.value = Mth.clamp(signal, 0, this.maxValue);
    }

    @Override
    public int insert(
            int amount,
            TransactionContext tx
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
            TransactionContext tx
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
    public void serialize(ValueOutput output) {
        output.putInt("value", this.value);
    }

    @Override
    public void deserialize(
            ValueInput input
    ) {
        this.value = input.getIntOr("value", 0);

    }
}