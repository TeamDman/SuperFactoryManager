package ca.teamdman.sfm.common.util;

import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;

import java.util.Iterator;
import java.util.function.LongConsumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/// A one-shot iterator over BlockPos objects backed by a LongIterator.
/// Note that the returned BlockPos objects are mutable and will be modified on each call to {@link #next()}.
/// Note that mutating the returned BlockPos objects will not mutate the underlying representation, however, calling {@link #remove()} will.
public class BlockPosIterator implements Iterator<BlockPos.MutableBlockPos>, Iterable<BlockPos.MutableBlockPos> {
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private final LongIterator innerIter;

    public BlockPosIterator(LongIterator longIterator) {

        this.innerIter = longIterator;
    }

    @Override
    public boolean hasNext() {

        return innerIter.hasNext();
    }

    @Override
    public BlockPos.MutableBlockPos next() {

        pos.set(innerIter.nextLong());
        return pos;
    }

    @Override
    public void remove() {

        innerIter.remove();
    }

    @SuppressWarnings("unused")
    public int skip(int n) {

        return innerIter.skip(n);
    }

    public void forEachLong(LongConsumer consumer) {
        while (innerIter.hasNext()) {
            consumer.accept(innerIter.nextLong());
        }
    }

    // Allows this to be the right-hand side of an enhanced for-loop
    @Override
    public Iterator<BlockPos.MutableBlockPos> iterator() {

        return this;
    }

    public Stream<BlockPos.MutableBlockPos> stream() {

        return StreamSupport.stream(spliterator(), false);
    }

}
