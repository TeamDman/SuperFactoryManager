package ca.teamdman.sfm.common.block_network;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.util.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class SFMBlockCapabilityCacheForLevel {
    // Position => Capability => Direction => CapabilityResult/LazyOptional
    // We don't use an EnumMap here for Direction because we need to support the null key
    private final BlockPosMap<Object2ObjectOpenHashMap<SFMBlockCapabilityKind<?>, SFMDirections.NullableDirectionEnumMap<SFMBlockCapabilityResult<?>>>> blockPosToCapKindToDirectionToCapResultMap = new BlockPosMap<>();

    // Chunk position => Set of Block positions
    private final ChunkPosMap<BlockPosSet> chunkPosToBlockPosMap = new ChunkPosMap<>();

    /// Used in 1.20.3+ for capability invalidation listening
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final World level;

    public SFMBlockCapabilityCacheForLevel(World level) {

        this.level = level;
    }

    public void clear() {

        blockPosToCapKindToDirectionToCapResultMap.clear();
        chunkPosToBlockPosMap.clear();
    }

    public int size() {

        return blockPosToCapKindToDirectionToCapResultMap
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

        var found = other.blockPosToCapKindToDirectionToCapResultMap.getFromPosition(pos);
        if (found != null) {
            blockPosToCapKindToDirectionToCapResultMap.put(pos.toLong(), new Object2ObjectOpenHashMap<>(found));
        }
        addToChunkMap(pos);
    }

    public <CAP> @Nullable SFMBlockCapabilityResult<CAP> getCapability(
            BlockPos pos,
            SFMBlockCapabilityKind<CAP> capKind,
            @Nullable EnumFacing direction
    ) {
        // Get the (pos, ...) entry
        var posEntry = blockPosToCapKindToDirectionToCapResultMap.getFromPosition(pos);
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

        for (var entry : other.blockPosToCapKindToDirectionToCapResultMap.long2ObjectEntrySet()) {
            long pos = entry.getLongKey();

            var capMap = entry.getValue();
            for (var e : capMap.entrySet()) {
                SFMBlockCapabilityKind<?> capKind = e.getKey();

                var dirMap = e.getValue();
                for (EnumFacing direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                    SFMBlockCapabilityResult<?> cap = dirMap.get(direction);
                    if (cap != null) {
                        putCapability(BlockPos.fromLong(pos), (SFMBlockCapabilityKind) capKind, direction, cap);
                    }
                }
            }
        }
    }

    public BlockPosIterator getPositions() {

        return blockPosToCapKindToDirectionToCapResultMap.positions();
    }

    public LongSet getPositionsRaw() {
        return blockPosToCapKindToDirectionToCapResultMap.keySet();
    }

    public void remove(
            BlockPos memberBlockPos,
            SFMBlockCapabilityKind<?> capKind,
            @Nullable EnumFacing direction
    ) {

        // Get the (pos, ...) entry.
        var posEntry = blockPosToCapKindToDirectionToCapResultMap.getFromPosition(memberBlockPos);
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
        blockPosToCapKindToDirectionToCapResultMap.removePosition(memberBlockPos);
        removeFromChunkMap(memberBlockPos);
    }

    public <CAP> void putCapability(
            BlockPos posIn,
            SFMBlockCapabilityKind<CAP> capKind,
            @Nullable EnumFacing direction,
            SFMBlockCapabilityResult<CAP> cap
    ) {

        // Ensure the position we bind to the listener lambda is immutable.
        final BlockPos pos = posIn.toImmutable();

        // Get the entry for (pos, ...capKind)
        Object2ObjectOpenHashMap<SFMBlockCapabilityKind<?>, SFMDirections.NullableDirectionEnumMap<SFMBlockCapabilityResult<?>>>
                posEntry = blockPosToCapKindToDirectionToCapResultMap.computeIfAbsent(pos.toLong(), k -> new Object2ObjectOpenHashMap<>());

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

    public void bustCacheForChunk(Chunk chunkAccess) {
        bustCacheForChunk(chunkAccess.getPos());
    }

    public void bustCacheForChunk(ChunkPos chunkPos) {

        BlockPosSet blockPositions = chunkPosToBlockPosMap.get(chunkPos);
        if (blockPositions != null) {
            blockPosToCapKindToDirectionToCapResultMap.removeAllPositions(blockPositions);
            chunkPosToBlockPosMap.remove(chunkPos);
        }
    }

    public void bustCacheForBlock(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        blockPosToCapKindToDirectionToCapResultMap.remove(pos.toLong());
        long posLong = ChunkPos.asLong(chunkPos.x, chunkPos.z);
        if (chunkPosToBlockPosMap.containsKey(posLong)) {
            Objects.requireNonNull(chunkPosToBlockPosMap.get(posLong)).remove(pos.toLong());
        }
    }


    private void addToChunkMap(BlockPos blockPos) {

        chunkPosToBlockPosMap
                .computeIfAbsent(
                        blockPos,
                       k -> new BlockPosSet()
                )
                .add(blockPos);
    }

    private void removeFromChunkMap(BlockPos blockPos) {

        BlockPosSet blockPosSet = chunkPosToBlockPosMap.get(blockPos);
        if (blockPosSet != null) {
            blockPosSet.remove(blockPos);
            if (blockPosSet.isEmpty()) {
                chunkPosToBlockPosMap.remove(blockPos);
            }
        }
    }

}
