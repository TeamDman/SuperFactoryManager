package ca.teamdman.sfm.common.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class BlockPosMap<T> extends Long2ObjectOpenHashMap<T> {
    public BlockPosMap(
            int expected,
            float f
    ) {

        super(expected, f);
    }

    public BlockPosMap(int expected) {

        super(expected);
    }

    public BlockPosMap() {

    }

    public BlockPosMap(
            Map<? extends Long, ? extends T> m,
            float f
    ) {

        super(m, f);
    }

    public BlockPosMap(Map<? extends Long, ? extends T> m) {

        super(m);
    }

    public BlockPosMap(
            Long2ObjectMap<T> m,
            float f
    ) {

        super(m, f);
    }

    public BlockPosMap(Long2ObjectMap<T> m) {

        super(m);
    }

    public BlockPosMap(
            long[] k,
            T[] v,
            float f
    ) {

        super(k, v, f);
    }

    public BlockPosMap(
            long[] k,
            T[] v
    ) {

        super(k, v);
    }

    @Override
    public @Nullable T get(long k) {

        return super.get(k);
    }

    public @Nullable T put(
            BlockPos blockPos,
            T member
    ) {

        return put(blockPos.asLong(), member);
    }

    public boolean containsKey(BlockPos blockPos) {

        return containsKey(blockPos.asLong());
    }

    public BlockPosIterator positions() {

        return new BlockPosIterator(keySet().longIterator());
    }

    public void removeAllPositions(BlockPosSet positionsInChunk) {
        keySet().removeAll(positionsInChunk);
    }

    public @Nullable T removePosition(BlockPos blockPos) {

        return remove(blockPos.asLong());
    }

    public @Nullable T getFromPosition(BlockPos blockPos) {

        return get(blockPos.asLong());
    }
}
