package ca.teamdman.sfm.common.block;

import net.minecraft.util.Mth;

/// Todo: make this used by the buffer, add datagen support instead of hardcoding.
public enum BufferBlockTier {
    Unit(1, 1),
    Paltry(1, 100),
    Basic(1, 1_000),
    Satisfying(27, 10_000),
    DoubleSatisfying(54, 20_000),
    MaxUnit(1, Long.MAX_VALUE),
    MaxGrande(1024, Long.MAX_VALUE);

    /// How many slots the buffer has.
    /// Scalar resource types always have 1 slot and numSlots*maxStackSize capacity.
    public final int numSlots;

    /// Note that we don't let items go above the default max stack size, this is for other resource types.
    public final long maxStackSize;

    public int getIntMaxStackSize() {
        return (int) Mth.clamp(maxStackSize, 0, Integer.MAX_VALUE);
    }
    public int getIntScalarMaxStackSize() {
        // assuming some overflow is happening, hence the max
        return (int) Mth.clamp(Math.max(maxStackSize * numSlots, maxStackSize), 0, Integer.MAX_VALUE);
    }
    public long getLongScalarMaxStackSize() {
        return Math.max(maxStackSize * numSlots, maxStackSize);
    }

    BufferBlockTier(
            int numSlots,
            long maxStackSize
    ) {
        this.numSlots = numSlots;
        this.maxStackSize = maxStackSize;
    }
}
