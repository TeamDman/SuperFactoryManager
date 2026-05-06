package ca.teamdman.sfm.common.capability;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public interface IRedstoneSignalStorage {
    /// @return The amount that was (or would have been, if simulated) inserted into the storage.
    int insert(int amount, TransactionContext tx);

    /// @return The amount that was (or would have been, if simulated) extracted from the storage.
    int extract(int amount, TransactionContext tx);

    /// @return The amount of redstone currently stored.
    int getStoredAmount();

    /// @return The maximum amount of redstone that can be stored.
    int getMaxStoredAmount();
}
