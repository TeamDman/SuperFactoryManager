package ca.teamdman.sfm.test.block_network;

import ca.teamdman.sfm.common.util.BlockPosMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public record SFMTestLevel<T>(
        String name,

        BlockPosMap<T> blocks
) {
    public SFMTestLevel(String name) {

        this(name, new BlockPosMap<>());

    }

    @Override
    public boolean equals(Object o) {

        if (!(o instanceof SFMTestLevel<?> that)) return false;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {

        return name.hashCode();
    }

    public void setBlock(BlockPos pos, @Nullable T block) {
        if (block == null) {
            blocks.removePosition(pos);
        } else {
            blocks.put(pos, block);
        }
    }

    public @Nullable T getBlock(BlockPos pos) {
        return blocks.getFromPosition(pos);
    }

    @Override
    public String toString() {

        return "SFMTestLevel{" +
               "name='" + name + '\'' +
               ", #blocks=" + blocks.size() +
               '}';
    }

}
