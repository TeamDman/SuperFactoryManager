package ca.teamdman.sfm.common.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

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

    public @Nullable T get(Chunk chunk) {

        return get(chunk.getPos());
    }

    public @Nullable T get(ChunkPos chunkPos) {

        return get(SFMBackportHelper.asChunkLong(chunkPos));
    }

    /// CORRECTNESS: make sure this is not a {@link BlockPos#toLong()}
    public @Nullable T get(long chunkPosLong) {

        return inner.get(chunkPosLong);
    }

    public @Nullable T get(BlockPos blockPos) {
        return inner.get(SFMBackportHelper.asChunkLong(blockPos));
    }


    public @Nullable T remove(Chunk chunk) {

        return remove(chunk.getPos());
    }

    public @Nullable T remove(ChunkPos chunkPos) {

        return remove(SFMBackportHelper.asChunkLong(chunkPos));
    }

    /// @param chunkPosLong Correctness: MUST come from {@link ChunkPos#asLong}, not to be confused with a {@link BlockPos#toLong()}
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

    /// @param chunkPosLong Correctness: must be from {@link ChunkPos#asLong}, not to be confused with a {@link BlockPos#toLong()}
    public T computeIfAbsent(
            long chunkPosLong,
            Long2ObjectFunction<? extends T> mappingFunction
    ) {
        Objects.requireNonNull(mappingFunction);
        T v;
        if ((v = inner.get(chunkPosLong)) == null) {
            T newValue;
            if ((newValue = mappingFunction.apply(chunkPosLong)) != null) {
                put(chunkPosLong, newValue);
                return newValue;
            }
        }

        return v;
    }

    public T computeIfAbsent(
            BlockPos memberBlockPos,
            Long2ObjectFunction<? extends T> mappingFunction
    ) {

        return computeIfAbsent(SFMBackportHelper.asChunkLong(memberBlockPos), mappingFunction);
    }

    public T computeIfAbsent(
            ChunkPos chunkPos,
            Long2ObjectFunction<? extends T> mappingFunction
    ) {

        return computeIfAbsent(SFMBackportHelper.asChunkLong(chunkPos), mappingFunction);
    }

    public @Nullable T remove(BlockPos blockPos) {

        return inner.remove(SFMBackportHelper.asChunkLong(blockPos));
    }

    public ObjectCollection<T> values() {

        return inner.values();
    }

    public ObjectSet<Long2ObjectMap.Entry<T>> entrySet() {

        return inner.long2ObjectEntrySet();
    }

    public interface Long2ObjectFunction<T> {
        T apply(long key);
    }

}
