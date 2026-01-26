package ca.teamdman.sfm.common.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.Nullable;

public class ChunkPosMap<T> {
    private final Long2ObjectMap<T> inner = new Long2ObjectOpenHashMap<>();

    public boolean isEmpty() {

        return inner.isEmpty();
    }

    public void clear() {

        inner.clear();
    }

    public @Nullable T put(
            long key,
            T value
    ) {

        return inner.put(key, value);
    }

    public @Nullable T get(ChunkAccess chunk) {

        return get(chunk.getPos());
    }

    public @Nullable T get(ChunkPos chunkPos) {

        return get(chunkPos.toLong());
    }

    /// CORRECTNESS: make sure this is not a {@link BlockPos#asLong()}
    public @Nullable T get(long chunkPosLong) {

        return inner.get(chunkPosLong);
    }

    public @Nullable T get(BlockPos blockPos) {

        return inner.get(ChunkPos.asLong(blockPos));
    }


    public @Nullable T remove(ChunkAccess chunk) {

        return remove(chunk.getPos());
    }

    public @Nullable T remove(ChunkPos chunkPos) {

        return remove(chunkPos.toLong());
    }

    /// @param chunkPosLong Correctness: MUST come from {@link ChunkPos#asLong}, not to be confused with a {@link BlockPos#asLong()}
    public @Nullable T remove(long chunkPosLong) {

        return inner.remove(chunkPosLong);
    }

    public LongSet keySet() {

        return inner.keySet();
    }

    public int size() {

        return inner.size();
    }

    public boolean containsKey(long key) {

        return inner.containsKey(key);
    }

    /// @param chunkPosLong Correctness: must be from {@link ChunkPos#asLong}, not to be confused with a {@link BlockPos#asLong()}
    public T computeIfAbsent(
            long chunkPosLong,
            Long2ObjectFunction<? extends T> mappingFunction
    ) {

        return inner.computeIfAbsent(chunkPosLong, mappingFunction);
    }

    public T computeIfAbsent(
            BlockPos memberBlockPos,
            Long2ObjectFunction<? extends T> mappingFunction
    ) {

        return computeIfAbsent(ChunkPos.asLong(memberBlockPos), mappingFunction);
    }

    public T computeIfAbsent(
            ChunkPos chunkPos,
            Long2ObjectFunction<? extends T> mappingFunction
    ) {

        return inner.computeIfAbsent(chunkPos.toLong(), mappingFunction);
    }

    public @Nullable T remove(BlockPos blockPos) {

        return inner.remove(ChunkPos.asLong(blockPos));
    }

    public ObjectCollection<T> values() {

        return inner.values();
    }

    public ObjectSet<Long2ObjectMap.Entry<T>> entrySet() {

        return inner.long2ObjectEntrySet();
    }

}
