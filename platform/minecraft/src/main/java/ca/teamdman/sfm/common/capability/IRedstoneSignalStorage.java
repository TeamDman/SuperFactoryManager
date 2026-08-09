package ca.teamdman.sfm.common.capability;

public interface IRedstoneSignalStorage {
    /// @return The amount that was (or would have been, if simulated) inserted into the storage.
    int insert(int amount, boolean simulate);

    /// @return The amount that was (or would have been, if simulated) extracted from the storage.
    int extract(int amount, boolean simulate);

    /// @return The amount of redstone currently stored.
    int getStoredAmount();

    /// @return The maximum amount of redstone that can be stored.
    int getMaxStoredAmount();

    /// @return If false, insert will always return 0.
    boolean canExtract();

    /// @return If false, extract will always return 0.
    boolean canReceive();
}
