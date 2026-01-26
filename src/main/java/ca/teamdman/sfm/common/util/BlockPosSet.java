package ca.teamdman.sfm.common.util;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;

import java.util.Iterator;

public record BlockPosSet(LongSet inner) implements Iterable<BlockPos> {
    public BlockPosSet() {

        this(new LongArraySet());
    }

    /// @return {@code false} if was already an element of the set, {@code true} otherwise
    public boolean add(BlockPos pos) {

        return inner.add(pos.asLong());

    }

    /// @return {@code true} if the collection was modified
    public boolean addAll(BlockPosSet otherChunkPositions) {

        return inner.addAll(otherChunkPositions.inner);

    }

    public LongIterator longIterator() {

        return inner.longIterator();
    }

    public boolean remove(long blockPos) {

        return inner.remove(blockPos);
    }

    public boolean isEmpty() {

        return inner.isEmpty();
    }

    public int size() {

        return inner.size();
    }

    public void clear() {

        inner.clear();
    }

    public boolean remove(BlockPos blockPos) {

        return inner.remove(blockPos.asLong());

    }

    public boolean contains(BlockPos pos) {

        return inner.contains(pos.asLong());
    }

    @Override
    public boolean equals(Object o) {

        if (!(o instanceof BlockPosSet that)) return false;

        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {

        return inner.hashCode();
    }

    public boolean add(long blockPosLong) {

        return inner.add(blockPosLong);
    }

    @Override
    public Iterator<BlockPos> iterator() {

        return new Iterator<BlockPos>() {
            final LongIterator inner = BlockPosSet.this.longIterator();

            @Override
            public boolean hasNext() {

                return inner.hasNext();
            }

            @Override
            public BlockPos next() {

                return BlockPos.of(inner.nextLong());
            }

            @Override
            public void remove() {

                inner.remove();
            }

            @SuppressWarnings("unused")
            public int skip(int n) {

                return inner.skip(n);
            }
        };
    }

}
