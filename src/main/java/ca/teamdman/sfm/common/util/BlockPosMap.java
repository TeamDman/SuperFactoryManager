package ca.teamdman.sfm.common.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnusedReturnValue")
public record BlockPosMap<T>(
        Long2ObjectOpenHashMap<T> inner
) {
    public BlockPosMap() {

        this(new Long2ObjectOpenHashMap<>());
    }

    public boolean isEmpty() {

        return inner.isEmpty();
    }

    public @Nullable T put(
            long key,
            T value
    ) {

        return inner.put(key, value);
    }

    public Long2ObjectMap.FastEntrySet<T> long2ObjectEntrySet() {

        return inner.long2ObjectEntrySet();
    }

    public T computeIfAbsent(
            long key,
            Long2ObjectFunction<? extends T> mappingFunction
    ) {

        return inner.computeIfAbsent(key, mappingFunction);
    }

    /// Correctness: make sure this is not a {@link net.minecraft.world.level.ChunkPos#asLong}
    public @Nullable T get(long blockPosLong) {

        return inner.get(blockPosLong);
    }

    public @Nullable T get(BlockPos blockPos) {

        return inner.get(blockPos.asLong());
    }

    /// Correctness: make sure this is not a {@link net.minecraft.world.level.ChunkPos#asLong}
    public @Nullable T remove(long blockPosLong) {

        return inner.remove(blockPosLong);
    }

    public @Nullable T remove(BlockPos blockPos) {

        return inner.remove(blockPos.asLong());
    }

    public LongSet keysAsLongSet() {

        return inner.keySet();
    }

    public BlockPosSet keysAsBlockPosSet() {

        return new BlockPosSet(inner.keySet());
    }

    public int size() {

        return inner.size();
    }

    public boolean containsKey(long key) {

        return inner.containsKey(key);
    }

    public void clear() {

        inner.clear();
    }

    public ObjectCollection<T> values() {

        return inner.values();
    }

    public void putAll(BlockPosMap<T> pos2TankMap) {

        inner.putAll(pos2TankMap.inner);

    }

    public boolean removeBlockPositions(BlockPosSet blockPosSet) {

        return removeBlockPositions(blockPosSet.inner());
    }

    public @Nullable T put(
            BlockPos blockPos,
            T member
    ) {

        return inner.put(blockPos.asLong(), member);
    }

    public boolean containsKey(BlockPos blockPos) {

        return inner.containsKey(blockPos.asLong());
    }

    public boolean removeBlockPositions(LongSet blockPosLongSet) {

        return inner.keySet().removeAll(blockPosLongSet);

    }

}
