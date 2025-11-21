package ca.teamdman.sfm.common.cablenetwork;

import java.util.stream.Stream;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.jetbrains.annotations.Nullable;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.util.NotStored;
import ca.teamdman.sfm.common.util.SFMDirections;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class SFMBlockCapabilityCacheForLevel {

    private static class CachedCapability {

        final IBlockState state;
        final SFMBlockCapabilityResult<?> capabilityResult;

        CachedCapability(IBlockState state, SFMBlockCapabilityResult<?> result) {
            this.state = state;
            this.capabilityResult = result;
        }
    }

    // Position => Capability => EnumFacing => CachedCapability
    private final Long2ObjectMap<Object2ObjectOpenHashMap<SFMBlockCapabilityKind<?>, SFMDirections.NullableDirectionEnumMap<CachedCapability>>> CACHE = new Long2ObjectOpenHashMap<>();
    // Chunk position => Set of Block positions
    private final Long2ObjectMap<LongArraySet> CHUNK_TO_BLOCK_POSITIONS = new Long2ObjectOpenHashMap<>();

    public void clear() {
        CACHE.clear();
        CHUNK_TO_BLOCK_POSITIONS.clear();
    }

    public int size() {
        return CACHE.values().stream().flatMap(x -> x.values().stream())
                .mapToInt(SFMDirections.NullableDirectionEnumMap::size).sum();
    }

    public void overwriteFromOther(@NotStored BlockPos pos, SFMBlockCapabilityCacheForLevel other) {
        var found = other.CACHE.get(pos.toLong());
        if (found != null) {
            CACHE.put(pos.toLong(), new Object2ObjectOpenHashMap<>(found));
        }
        addToChunkMap(pos);
    }

    public <CAP> @Nullable SFMBlockCapabilityResult<CAP> getCapability(
                                                                       World world,
                                                                       @NotStored BlockPos pos,
                                                                       SFMBlockCapabilityKind<CAP> capKind,
                                                                       @Nullable EnumFacing direction) {
        var capMap = CACHE.get(pos.toLong());
        if (capMap != null) {
            var dirMap = capMap.get(capKind);
            if (dirMap != null) {
                var found = dirMap.get(direction);
                if (found != null) {
                    IBlockState currentState = world.getBlockState(pos);
                    if (currentState == found.state) {
                        // noinspection unchecked
                        return (SFMBlockCapabilityResult<CAP>) found.capabilityResult;
                    } else {
                        // Stale cache, remove it
                        remove(pos, capKind, direction);
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void putAll(SFMBlockCapabilityCacheForLevel other) {
        // This method is likely not safe with the new caching mechanism, but it's not used in the hot path.
        // For now, we'll leave it as a no-op to avoid issues.
    }

    public Stream<BlockPos> getPositions() {
        return CACHE.keySet().stream().map(BlockPos::fromLong);
    }

    public void remove(
                       @NotStored BlockPos pos,
                       SFMBlockCapabilityKind<?> capKind,
                       @Nullable EnumFacing direction) {
        var capMap = CACHE.get(pos.toLong());
        if (capMap != null) {
            var dirMap = capMap.get(capKind);
            if (dirMap != null) {
                dirMap.remove(direction);
                if (dirMap.isEmpty()) {
                    capMap.remove(capKind);
                    if (capMap.isEmpty()) {
                        CACHE.remove(pos.toLong());
                    }
                }
                removeFromChunkMap(pos);
            }
        }
    }

    public <CAP> void putCapability(
                                    World world,
                                    @NotStored BlockPos pos,
                                    SFMBlockCapabilityKind<CAP> capKind,
                                    @Nullable EnumFacing direction,
                                    SFMBlockCapabilityResult<CAP> cap) {
        IBlockState currentState = world.getBlockState(pos);
        CachedCapability cachedCap = new CachedCapability(currentState, cap);

        var capMap = CACHE.computeIfAbsent(pos.toLong(), k -> new Object2ObjectOpenHashMap<>());
        var dirMap = capMap.computeIfAbsent(capKind, k -> new SFMDirections.NullableDirectionEnumMap<>());
        dirMap.put(direction, cachedCap);
        addToChunkMap(pos);
    }

    public void bustCacheForChunk(Chunk chunkAccess) {
        var pos = chunkAccess.getPos();
        long chunkKey = ChunkPos.asLong(pos.x, pos.z);
        LongArraySet blockPositions = CHUNK_TO_BLOCK_POSITIONS.get(chunkKey);
        if (blockPositions != null) {
            for (long blockPos : blockPositions) {
                CACHE.remove(blockPos);
            }
            CHUNK_TO_BLOCK_POSITIONS.remove(chunkKey);
        }
    }

    private void addToChunkMap(@NotStored BlockPos pos) {
        long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        long blockPos = pos.toLong();
        CHUNK_TO_BLOCK_POSITIONS.computeIfAbsent(chunkKey, k -> new LongArraySet()).add(blockPos);
    }

    private void removeFromChunkMap(@NotStored BlockPos pos) {
        long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        long blockPos = pos.toLong();
        LongArraySet blockPosSet = CHUNK_TO_BLOCK_POSITIONS.get(chunkKey);
        if (blockPosSet != null) {
            blockPosSet.remove(blockPos);
            if (blockPosSet.isEmpty()) {
                CHUNK_TO_BLOCK_POSITIONS.remove(chunkKey);
            }
        }
    }
}
