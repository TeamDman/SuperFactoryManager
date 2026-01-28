package ca.teamdman.sfm.common.block_network;

import ca.teamdman.sfm.common.util.*;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/// A block network is a contiguous chain of blocks touching in the world.
/// Block networks are tracked by a {@link BlockNetworkManager}.
/// Methods are intentionally package-private since the block network manager needs to be kept in sync.
///
/// The {@link LEVEL} generic is used to enable unit testing without instantiating full Minecraft level objects.
public class BlockNetwork<LEVEL, T> {
    private final LEVEL level;

    private final BlockPosMap<T> membersByBlockPosition;

    private final ChunkPosMap<BlockPosSet> memberBlockPositionsByChunk;

    private final BlockNetworkMemberFilterMapper<LEVEL, T> memberFilterMapper;

    private final BlockNetworkConstructor<LEVEL, T, ? extends BlockNetwork<LEVEL, T>> networkConstructor;

    public BlockNetwork(
            LEVEL level,
            BlockNetworkMemberFilterMapper<LEVEL, T> memberFilterMapper
    ) {
        this(level, memberFilterMapper, BlockNetwork::new);
    }

    public BlockNetwork(
            LEVEL level,
            BlockNetworkMemberFilterMapper<LEVEL, T> memberFilterMapper,
            BlockNetworkConstructor<LEVEL, T, ? extends BlockNetwork<LEVEL, T>> networkConstructor
    ) {

        this.level = level;
        this.membersByBlockPosition = new BlockPosMap<>();
        this.memberBlockPositionsByChunk = new ChunkPosMap<>();
        this.memberFilterMapper = memberFilterMapper;
        this.networkConstructor = networkConstructor;
    }

    public LEVEL level() {

        return level;
    }

    public BlockPosMap<T> members() {

        return membersByBlockPosition;
    }

    public ChunkPosMap<BlockPosSet> memberBlockPositionsByChunk() {

        return memberBlockPositionsByChunk;
    }

    public boolean isEmpty() {

        return membersByBlockPosition.isEmpty();
    }

    public boolean usesChunk(ChunkPos chunkPos) {

        return usesChunk(chunkPos.toLong());
    }

    public boolean usesChunk(long chunkPos) {

        return memberBlockPositionsByChunk.containsKey(chunkPos);
    }

    public boolean isMember(BlockPos blockPos) {

        return membersByBlockPosition.containsKey(blockPos);
    }

    public int size() {

        return membersByBlockPosition.size();
    }

    public boolean containsBlockPos(BlockPos memberBlockPos) {

        return membersByBlockPosition.containsKey(memberBlockPos);
    }

    @Nullable T getCandidate(BlockPos pos) {

        return this.memberFilterMapper.getNetworkMember(level, pos);
    }

    Stream<Pair<BlockPos, T>> discoverCandidatesFromLevel(
            BlockPos start
    ) {

        return SFMStreamUtils.getRecursiveStream(
                (currentBlockPos, next, results) -> {
                    @Nullable T member = BlockNetwork.this.getCandidate(currentBlockPos);
                    if (member == null) return;

                    // Track the return value
                    results.accept(Pair.of(currentBlockPos, member));

                    // Schedule the tank neighbours for traversal
                    for (Direction d : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                        next.accept(currentBlockPos.relative(d));
                    }
                },
                start
        );
    }

    Stream<Pair<BlockPos, T>> discoverCandidatesFromSelf(
            BlockPos start
    ) {

        return SFMStreamUtils.getRecursiveStream(
                (currentBlockPos, next, results) -> {
                    // Only traverse positions that have a block entity in the cache
                    T member = membersByBlockPosition.get(currentBlockPos);
                    if (member == null) return;

                    // Track the return value
                    results.accept(Pair.of(currentBlockPos, member));

                    // Schedule the neighbour positions for traversal
                    for (Direction d : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                        next.accept(currentBlockPos.relative(d));
                    }
                },
                start
        );
    }

    void purgeChunk(ChunkPos chunkPos) {

        BlockPosSet positionsInChunk = memberBlockPositionsByChunk.get(chunkPos);
        if (positionsInChunk != null) {
            membersByBlockPosition.removeBlockPositions(positionsInChunk);
            memberBlockPositionsByChunk.remove(chunkPos);
        }
    }

    void addMember(Pair<BlockPos, T> pair) {

        addMember(pair.getFirst(), pair.getSecond());
    }

    void addMember(
            BlockPos memberBlockPos,
            T member
    ) {

        membersByBlockPosition.put(memberBlockPos, member);

        memberBlockPositionsByChunk
                .computeIfAbsent(
                        memberBlockPos,
                        (Long2ObjectFunction<? extends BlockPosSet>) k -> new BlockPosSet()
                )
                .add(memberBlockPos);
    }

    void removeMember(BlockPos blockPos) {

        boolean wasPresent = membersByBlockPosition.remove(blockPos) != null;
        if (wasPresent) {
            BlockPosSet chunkMemberBlockPositions = Objects.requireNonNull(memberBlockPositionsByChunk.get(blockPos));
            chunkMemberBlockPositions.remove(blockPos);
            if (chunkMemberBlockPositions.isEmpty()) {
                memberBlockPositionsByChunk.remove(blockPos);
            }
        }
    }

    /// Discover from the other network into this one.
    private void populateFromPosition(
            BlockNetwork<LEVEL, T> other,
            BlockPos startPos
    ) {

        other.discoverCandidatesFromSelf(startPos).forEach((pair) -> {
            BlockPos memberBlockPos = pair.getFirst();
            T member = pair.getSecond();
            addMember(memberBlockPos, member);
        });
    }

    void addAllFromOtherNetwork(BlockNetwork<LEVEL, T> other) {

        membersByBlockPosition.putAll(other.membersByBlockPosition);
        for (LongIterator iterator = other.memberBlockPositionsByChunk.keySet().iterator(); iterator.hasNext(); ) {
            long chunkPosLong = iterator.nextLong();
            BlockPosSet thisChunkPositions = this.memberBlockPositionsByChunk.computeIfAbsent(
                    chunkPosLong,
                    k -> new BlockPosSet()
            );
            BlockPosSet otherChunkPositions = other.memberBlockPositionsByChunk.get(chunkPosLong);
            assert otherChunkPositions != null;
            thisChunkPositions.addAll(otherChunkPositions);
        }
    }

    /// Determine the networks that would result from this network having the given position removed.
    List<BlockNetwork<LEVEL, T>> splitRemoveMember(BlockPos blockPos) {

        // Remove the position from the network
        removeMember(blockPos);

        // Prepare the list of resulting branch networks
        List<BlockNetwork<LEVEL, T>> branches = new ArrayList<>();

        // For each neighbour of the removed position, identify branch uniqueness
        for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {

            // Get the neighbour position
            BlockPos neighbourPos = blockPos.relative(direction);

            // Skip if the neighbour position is not a member of this network
            if (!isMember(neighbourPos)) continue;

            // Skip if the neighbour position is a member of one of the branches we have already discovered
            boolean alreadySeen = false;
            for (BlockNetwork<LEVEL, T> branch : branches) {
                if (branch.isMember(neighbourPos)) {
                    alreadySeen = true;
                    break;
                }
            }
            if (alreadySeen) continue;

            // Create the new network to hold the branch
            BlockNetwork<LEVEL, T> branch = networkConstructor.create(level, memberFilterMapper);

            // Populate the branch from this network
            branch.populateFromPosition(this, neighbourPos);

            // Track the branch to be returned
            branches.add(branch);
        }
        return branches;
    }

}
