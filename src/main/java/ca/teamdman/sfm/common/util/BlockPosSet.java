package ca.teamdman.sfm.common.util;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

public final class BlockPosSet extends LongOpenHashSet {
    public BlockPosSet(
            int expected,
            float f
    ) {

        super(expected, f);
    }

    public BlockPosSet(int expected) {

        super(expected);
    }

    public BlockPosSet() {

    }

    public BlockPosSet(
            Collection<? extends Long> c,
            float f
    ) {

        super(c, f);
    }

    public BlockPosSet(
            LongCollection c,
            float f
    ) {

        super(c, f);
    }

    public BlockPosSet(LongCollection c) {

        super(c);
    }

    public BlockPosSet(
            LongIterator i,
            float f
    ) {

        super(i, f);
    }

    public BlockPosSet(LongIterator i) {

        super(i);
    }

    public BlockPosSet(
            Iterator<?> i,
            float f
    ) {

        super(i, f);
    }

    public BlockPosSet(Iterator<?> i) {

        super(i);
    }

    public BlockPosSet(
            long[] a,
            int offset,
            int length,
            float f
    ) {

        super(a, offset, length, f);
    }

    public BlockPosSet(
            long[] a,
            int offset,
            int length
    ) {

        super(a, offset, length);
    }

    public BlockPosSet(
            long[] a,
            float f
    ) {

        super(a, f);
    }

    public BlockPosSet(long[] a) {

        super(a);
    }

    public BlockPosSet(Collection<BlockPos> blockPosCollection) {
        this(blockPosCollection.size());
        blockPosCollection.forEach(this::add);
    }

    /// @return {@code false} if was already an element of the set, {@code true} otherwise
    public boolean add(BlockPos pos) {

        return add(pos.toLong());

    }


    // can't be called `addAll` because of type erasure
    public void addAllPositions(Collection<BlockPos> other) {

        for (BlockPos blockPos : other) {
            add(blockPos);
        }
    }

    public boolean remove(BlockPos blockPos) {

        return remove(blockPos.toLong());

    }

    public boolean contains(BlockPos pos) {

        return contains(pos.toLong());
    }

    public BlockPosIterator blockPosIterator() {

        return new BlockPosIterator(BlockPosSet.this.longIterator());
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean removeIfPosition(Predicate<BlockPos> blockPosPredicate) {

        boolean removed = false;
        for (Iterator<BlockPos.MutableBlockPos> iterator = blockPosIterator().iterator(); iterator.hasNext(); ) {
            BlockPos.MutableBlockPos pos = iterator.next();
            if (blockPosPredicate.test(pos)) {
                iterator.remove();
                removed = true;
            }
        }
        return removed;
    }

    public static Collector<BlockPos, BlockPosSet, BlockPosSet> collector() {

        return Collector.of(
                BlockPosSet::new,
                BlockPosSet::add,
                (a, b) -> {
                    a.addAll(b);
                    return a;
                }
        );
    }

    public static BlockPosSet of(BlockPos... pos) {
        BlockPosSet rtn = new BlockPosSet();
        //noinspection ManualArrayToCollectionCopy // doesn't apply since addAll expects longs
        for (BlockPos p : pos) {
            rtn.add(p);
        }
        return rtn;

    }

    public @Nullable StructureBoundingBox boundingBox() {
        return SFMBackportHelper.boundingBoxOf(this.blockPosIterator());
    }

    public LongStream longStream() {
        LongIterator iterator = this.iterator();

        Spliterator.OfLong spliterator =
                Spliterators.spliteratorUnknownSize(
                        new java.util.PrimitiveIterator.OfLong() {
                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public long nextLong() {
                                return iterator.nextLong();
                            }
                        },
                        Spliterator.DISTINCT | Spliterator.NONNULL
                );

        return StreamSupport.longStream(spliterator, false);
    }

}
