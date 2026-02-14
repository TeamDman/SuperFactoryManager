package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.util.Mth;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

/// A pretend container that can read the 'redstone units' of a block
public class RedstoneSignalBlockReader implements IRedstoneSignalStorage, INBTSerializable<NBTTagInt> {

    private final World world;
    private final BlockPos pos;
    @Nullable
    private final EnumFacing facing;

    public RedstoneSignalBlockReader(World world, BlockPos pos, @Nullable EnumFacing facing) {
       this.world = world;
       this.pos = pos;
       this.facing = facing;
    }

    @Override
    public int insert(
            int amount,
            boolean simulate
    ) {
       return 0;
    }

    @Override
    public int extract(
            int amount,
            boolean simulate
    ) {
        int value = getStoredAmount();
        int extract = Mth.clamp(amount, 0, value);

        return extract;
    }

    @Override
    public int getStoredAmount() {
        return facing != null ? world.getRedstonePower(pos, facing) : world.getRedstonePowerFromNeighbors(pos);
    }

    @Override
    public int getMaxStoredAmount() {
        return 15;
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

    }

    @Override
    public void deserializeNBT(NBTTagInt nbt) {

    }

    @Override
    public NBTTagInt serializeNBT() {
        return new NBTTagInt(0);
    }
}