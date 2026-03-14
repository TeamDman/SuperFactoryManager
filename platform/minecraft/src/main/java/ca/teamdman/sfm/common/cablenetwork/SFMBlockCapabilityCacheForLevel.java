package ca.teamdman.sfm.common.cablenetwork;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.util.SFMDirections;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public class SFMBlockCapabilityCacheForLevel {
    // Position => Capability => Direction => LazyOptional
    // We don't use an EnumMap here for Direction because we need to support the null key
    private final Long2ObjectMap<Object2ObjectOpenHashMap<SFMBlockCapabilityKind<?>, SFMDirections.NullableDirectionEnumMap<SFMBlockCapabilityResult<?>>>> CACHE = new Long2ObjectOpenHashMap<>();

    // Chunk position => Set of Block positions
    private final Long2ObjectMap<LongArraySet> CHUNK_TO_BLOCK_POSITIONS = new Long2ObjectOpenHashMap<>();

    /// Used in 1.20.3+ for capability invalidation listening
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final Level level;

    public SFMBlockCapabilityCacheForLevel(Level level) {

        this.level = level;
    }

    public void clear() {

        CACHE.clear();
        CHUNK_TO_BLOCK_POSITIONS.clear();
    }

    public int size() {

        return CACHE
                .values()
                .stream()
                .flatMap(x -> x.values().stream())
                .mapToInt(SFMDirections.NullableDirectionEnumMap::size)
                .sum();
    }

    public void overwriteFromOther(
            BlockPos pos,
            SFMBlockCapabilityCacheForLevel other
    ) {

        var found = other.CACHE.get(pos.asLong());
        if (found != null) {
            CACHE.put(pos.asLong(), new Object2ObjectOpenHashMap<>(found));
        }
        addToChunkMap(pos);
    }

    public <CAP> @Nullable SFMBlockCapabilityResult<CAP> getCapability(
            BlockPos pos,
            SFMBlockCapabilityKind<CAP> capKind,
            @Nullable Direction direction
    ) {
        // Get the (pos, ...) entry
        var posEntry = CACHE.get(pos.asLong());
        if (posEntry == null) {
            return null;
        }

        // Get the (pos, capKind, ...direction) entry
        var capKindEntry = posEntry.get(capKind);
        if (capKindEntry == null) {
            return null;
        }

        // Get the (pos, capKind, direction) entry
        var found = capKindEntry.get(direction);
        if (found == null) {
            return null;
        }

        // Return the cached capability result
        //noinspection unchecked
        return (SFMBlockCapabilityResult<CAP>) found;

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void putAll(SFMBlockCapabilityCacheForLevel other) {

        for (var entry : other.CACHE.long2ObjectEntrySet()) {
            long pos = entry.getLongKey();

            var capMap = entry.getValue();
            for (var e : capMap.entrySet()) {
                SFMBlockCapabilityKind<?> capKind = e.getKey();

                var dirMap = e.getValue();
                for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                    SFMBlockCapabilityResult<?> cap = dirMap.get(direction);
                    if (cap != null) {
                        putCapability(BlockPos.of(pos), (SFMBlockCapabilityKind) capKind, direction, cap);
                    }
                }
            }
        }
    }

    public Stream<BlockPos> getPositions() {

        return CACHE.keySet().longStream().mapToObj(BlockPos::of);
    }

    public void remove(
            BlockPos pos,
            SFMBlockCapabilityKind<?> capKind,
            @Nullable Direction direction
    ) {

        // Get the (pos, ...) entry.
        var posEntry = CACHE.get(pos.asLong());
        if (posEntry == null) {
            return;
        }

        // Get the (pos, capKind, ...directions) entry.
        var capKindEntry = posEntry.get(capKind);
        if (capKindEntry == null) {
            return;
        }

        // Remove the given direction.
        capKindEntry.remove(direction);

        // We are done if there are other directions keeping the cache entry for (pos, capKind, ...) alive
        if (!capKindEntry.isEmpty()) {
            return;
        }

        // capKind in (pos, capKind, ...) is now empty, remove it.
        posEntry.remove(capKind);

        // We are done if there exists other (pos, ...) entries.
        if (!posEntry.isEmpty()) {
            return;
        }

        // pos is now empty, remove it.
        CACHE.remove(pos.asLong());
        removeFromChunkMap(pos);
    }

    public <CAP> void putCapability(
            BlockPos posIn,
            SFMBlockCapabilityKind<CAP> capKind,
            @Nullable Direction direction,
            SFMBlockCapabilityResult<CAP> cap
    ) {

        // Ensure the position we bind to the listener lambda is immutable.
        final BlockPos pos = posIn.immutable();

        // Get the entry for (pos, ...capKind)
        Object2ObjectOpenHashMap<SFMBlockCapabilityKind<?>, SFMDirections.NullableDirectionEnumMap<SFMBlockCapabilityResult<?>>>
                posEntry = CACHE.computeIfAbsent(pos.asLong(), k -> new Object2ObjectOpenHashMap<>());

        // Get the entry for the (pos, capKind, ...direction)
        SFMDirections.NullableDirectionEnumMap<SFMBlockCapabilityResult<?>>
                capKindEntry = posEntry.computeIfAbsent(capKind, k -> new SFMDirections.NullableDirectionEnumMap<>());

        // Track the (pos, capKind, direction) entry
        capKindEntry.put(direction, cap);
        addToChunkMap(pos);

        // Register a listener to remove the cache entry when the world tells us to.
        cap.addInvalidationListener(__ -> this.remove(
                pos,
                capKind,
                direction
        ));
    }

    public void bustCacheForChunk(ChunkAccess chunkAccess) {

        long chunkKey = chunkAccess.getPos().toLong();
        LongArraySet blockPositions = CHUNK_TO_BLOCK_POSITIONS.get(chunkKey);
        if (blockPositions != null) {
            for (var blockPos : blockPositions) {
                CACHE.remove(blockPos.longValue());
            }
            CHUNK_TO_BLOCK_POSITIONS.remove(chunkKey);
        }
    }

    private void addToChunkMap(BlockPos pos) {

        ChunkPos chunkPos = new ChunkPos(pos);
        long chunkKey = chunkPos.toLong();
        long blockPos = pos.asLong();
        CHUNK_TO_BLOCK_POSITIONS.computeIfAbsent(chunkKey, k -> new LongArraySet()).add(blockPos);
    }

    private void removeFromChunkMap(BlockPos pos) {

        ChunkPos chunkPos = new ChunkPos(pos);
        long chunkKey = chunkPos.toLong();
        long blockPos = pos.asLong();
        LongArraySet blockPosSet = CHUNK_TO_BLOCK_POSITIONS.get(chunkKey);
        if (blockPosSet != null) {
            blockPosSet.remove(blockPos);
            if (blockPosSet.isEmpty()) {
                CHUNK_TO_BLOCK_POSITIONS.remove(chunkKey);
            }
        }
    }

}
