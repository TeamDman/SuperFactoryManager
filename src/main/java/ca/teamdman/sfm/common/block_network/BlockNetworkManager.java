package ca.teamdman.sfm.common.block_network;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.util.*;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// A data structure for handling networks of blocks governed by contiguous-touching rules in a level.
///
/// Because this area deals with both block positions and chunk positions, variables should be named to clearly indicate which is being used.
/// E.g., `memberPos` should be `memberBlockPos`
///
/// The {@link LEVEL} generic is used to enable unit testing without instantiating full Minecraft level objects.
public class BlockNetworkManager<LEVEL, T, NETWORK extends BlockNetwork<LEVEL, T>> {
    /// The maximum number of members in a network before skipping expensive split operations.
    /// Networks larger than this will be cleared and lazily rebuilt instead of split.
    public static final int SPLIT_SIZE_THRESHOLD = 256;

    private final Map<LEVEL, BlockPosMap<NETWORK>> networksByLevelBlockPos = new Object2ObjectOpenHashMap<>();

    private final Map<LEVEL, ChunkPosMap<Set<NETWORK>>> networksByLevelChunk = new Object2ObjectOpenHashMap<>();

    private final Map<LEVEL, Set<NETWORK>> networksByLevel = new Object2ObjectOpenHashMap<>();

    private final BlockNetworkMemberFilterMapper<LEVEL, T> memberFilterMapper;

    private final BlockNetworkConstructor<LEVEL, T, NETWORK> networkConstructor;

    public BlockNetworkManager(
            BlockNetworkMemberFilterMapper<LEVEL, T> memberFilterMapper,
            BlockNetworkConstructor<LEVEL, T, NETWORK> networkConstructor
    ) {

        this.memberFilterMapper = memberFilterMapper;
        this.networkConstructor = networkConstructor;
    }

    /// Called when the network structure changes.
    /// Override to add custom behavior like additional logging.
    /// Default implementation prints diagnostics and asserts invariants in IDE.
    protected void onChange() {
        printChangeDiagnostics();
        if (SFMEnvironmentUtils.isInIDE()) {
            assertInvariants();
        }
    }

    public @Nullable NETWORK getNetwork(
            LEVEL level,
            BlockPos blockPos
    ) {

        BlockPosMap<NETWORK> blockPosMap = networksByLevelBlockPos.get(level);
        if (blockPosMap == null) return null;
        return blockPosMap.get(blockPos);
    }

    public BlockPosMap<NETWORK> getNetworksForLevel(LEVEL level) {

        BlockPosMap<NETWORK> blockPosMap = networksByLevelBlockPos.get(level);
        if (blockPosMap == null) return new BlockPosMap<>();
        return blockPosMap;
    }

    public void clearChunk(
            LEVEL level,
            ChunkPos chunkPos
    ) {

        ChunkPosMap<Set<NETWORK>> levelChunkPosMap = networksByLevelChunk.get(level);
        if (levelChunkPosMap == null) return;
        Set<NETWORK> networksForChunk = levelChunkPosMap.get(chunkPos);
        if (networksForChunk == null) return;
        for (NETWORK network : networksForChunk) {
            network.purgeChunk(chunkPos);
        }
        onChange();
    }

    public @Nullable NETWORK getOrRegisterNetworkFromMemberPosition(
            LEVEL level,
            BlockPos memberBlockPos
    ) {
        // Return existing network if one present.
        @Nullable NETWORK existing = getNetwork(level, memberBlockPos);
        if (existing != null) return existing;

        // No network exists for this position yet.
        // We will update an adjacent network if one exists to include this position.
        // Otherwise, we will create a new network.

        // Ensure the position is a valid member
        @Nullable T member = memberFilterMapper.getNetworkMember(level, memberBlockPos);
        if (member == null) return null;

        // Scan neighbours to find networks and unclaimed members
        ArrayDeque<BlockPos> unclaimedNeighbourPositions = new ArrayDeque<>(6);
        List<NETWORK> neighbouringNetworks = new ArrayList<>();

        BlockPos.MutableBlockPos neighbourBlockPosition = new BlockPos.MutableBlockPos();
        for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            neighbourBlockPosition.set(memberBlockPos).move(direction);
            @Nullable NETWORK neighbourNetwork = getNetwork(level, neighbourBlockPosition);
            if (neighbourNetwork == null) {
                /// We do not need to check if this neighbour block is a valid candidate.
                /// {@link BlockNetwork#discoverCandidatesFromLevel(BlockPos)} will check for us.
                unclaimedNeighbourPositions.add(neighbourBlockPosition.immutable());
            } else {
                neighbouringNetworks.add(neighbourNetwork);
            }
        }

        // Determine the result network
        NETWORK resultNetwork;
        Iterator<NETWORK> neighbouringNetworkIterator = neighbouringNetworks.iterator();
        if (neighbouringNetworkIterator.hasNext()) {
            // A neighbour network exists, it will absorb the other networks
            resultNetwork = neighbouringNetworkIterator.next();
            // Update membership
            resultNetwork.addMember(memberBlockPos, member);
            // Update tracking
            trackMemberBlockPosForNetwork(memberBlockPos, resultNetwork);
        } else {
            // No neighbouring networks exist, we construct a new network
            resultNetwork = networkConstructor.create(level, memberFilterMapper);
            // Apply discovery; the candidates include the member position itself
            resultNetwork.discoverCandidatesFromLevel(memberBlockPos).forEach(pair -> {
                // Update membership
                resultNetwork.addMember(pair);
                // Update tracking
                trackMemberBlockPosForNetwork(pair.getFirst(), resultNetwork);
            });
        }

        // Merge any neighbouring networks
        while (neighbouringNetworkIterator.hasNext()) {
            // Get the old network
            NETWORK oldNetwork = neighbouringNetworkIterator.next();
            // Merge the old network into the result network
            resultNetwork.addAllFromOtherNetwork(oldNetwork);
            // Update tracking
            trackNetworkTransfer(oldNetwork, resultNetwork);
        }

        // Add any unclaimed members
        for (BlockPos unclaimedNeighbourPosition : unclaimedNeighbourPositions) {
            resultNetwork.discoverCandidatesFromLevel(unclaimedNeighbourPosition).forEach(pair -> {
                // Update membership
                resultNetwork.addMember(pair);
                // Update tracking
                trackMemberBlockPosForNetwork(pair.getFirst(), resultNetwork);
            });
        }

        onChange();
        return resultNetwork;
    }

    /// MUST be called to keep the networks in sync
    public @Nullable NETWORK onMemberAddedToLevel(
            LEVEL level,
            BlockPos memberBlockPos
    ) {

        return getOrRegisterNetworkFromMemberPosition(
                level,
                memberBlockPos
        );
    }

    /// MUST be called to keep the networks in sync
    @SuppressWarnings("unchecked")
    public List<NETWORK> onMemberRemovedFromLevel(
            LEVEL level,
            BlockPos memberBlockPos
    ) {

        // Identify the network associated with the member position
        NETWORK oldNetwork = getNetwork(level, memberBlockPos);
        if (oldNetwork == null) return List.of();

        // Untrack the position as the member has been removed from the level
        untrackMemberFromNetwork(memberBlockPos, oldNetwork);

        // For large networks, skip expensive split and clear the network for lazy rebuild
        if (oldNetwork.size() > SPLIT_SIZE_THRESHOLD) {
            untrackNetwork(oldNetwork);
            if (SFMEnvironmentUtils.isInIDE()) {
                assertNetworkForgotten(oldNetwork);
            }
            onChange();
            return List.of();
        }

        // Identify the networks that result from the removal of the position
        // The cast is safe because splitRemoveMember uses the networkConstructor which produces NETWORK instances
        List<NETWORK> resultingNetworks = (List<NETWORK>) oldNetwork.splitRemoveMember(memberBlockPos);

        // Track the resulting networks
        for (NETWORK newNetwork : resultingNetworks) {
            trackNetworkTransfer(oldNetwork, newNetwork);
        }

        // The old network should no longer be tracked as all lookup table entries
        // have been clobbered by the split after untracking the removed member position
        if (SFMEnvironmentUtils.isInIDE()) {
            assertNetworkForgotten(oldNetwork);
        }

        onChange();
        return resultingNetworks;
    }


    public void assertNetworkForgotten(BlockNetwork<LEVEL, T> network) {

        LEVEL level = network.level();

        // Check the level lookup
        if (networksByLevel.getOrDefault(level, Collections.emptySet()).contains(network)) {
            throw new IllegalStateException("Network still tracked in level lookup");
        }

        // Check the chunk lookup
        if (networksByLevelChunk
                .getOrDefault(level, new ChunkPosMap<>())
                .values()
                .stream()
                .anyMatch(list -> list.contains(network))) {
            throw new IllegalStateException("Network still tracked in chunk lookup");
        }

        // Check the block position lookup
        if (networksByLevelBlockPos.getOrDefault(level, new BlockPosMap<>()).values().contains(network)) {
            throw new IllegalStateException("Network still tracked in block position lookup");
        }
    }

    public void assertInvariants() {

        try {
            for (Map.Entry<LEVEL, Set<NETWORK>> entry : networksByLevel.entrySet()) {
                LEVEL level = entry.getKey();
                BlockPosMap<NETWORK> networksByPositionLookup = networksByLevelBlockPos.get(level);
                if (networksByPositionLookup == null) {
                    throw new IllegalStateException("Level " + level + " has no block position lookup");
                }
                Set<NETWORK> networksForLevel = entry.getValue();
                BlockPosSet seen = new BlockPosSet();
                for (NETWORK network : networksForLevel) {
                    for (BlockPos blockPos : network.members().keysAsBlockPosSet()) {
                        // Assert that no position belongs to multiple networks in this level
                        boolean modified = seen.add(blockPos);
                        if (!modified) {
                            throw new IllegalStateException("Position "
                                                            + blockPos
                                                            + " is in multiple networks in level "
                                                            + level);
                        }

                        // Assert that the position is present in the lookup map
                        NETWORK foundNetwork = networksByPositionLookup.get(blockPos);
                        if (foundNetwork == null) {
                            throw new IllegalStateException("Position "
                                                            + blockPos
                                                            + " is not in the position lookup for level "
                                                            + level);
                        } else if (foundNetwork != network) {
                            throw new IllegalStateException("Position "
                                                            + blockPos
                                                            + " is in two networks, expected "
                                                            + network
                                                            + " but found "
                                                            + foundNetwork);
                        }

                        // Assert that the position is present in the chunk lookup map
                        Set<NETWORK> networksInChunk = networksByLevelChunk.get(level).get(blockPos);
                        if (networksInChunk == null) {
                            throw new IllegalStateException("Position "
                                                            + blockPos
                                                            + " is not in a tracked chunk for level "
                                                            + level);
                        }
                        if (!networksInChunk.contains(network)) {
                            throw new IllegalStateException("Position "
                                                            + blockPos
                                                            + " is in network "
                                                            + foundNetwork
                                                            + " but not "
                                                            + network);
                        }
                    }
                }
            }

            for (Map.Entry<LEVEL, ChunkPosMap<Set<NETWORK>>> entry : networksByLevelChunk.entrySet()) {
                LEVEL level = entry.getKey();
                ChunkPosMap<Set<NETWORK>> networksByChunk = entry.getValue();
                Set<NETWORK> networksInLevel = networksByLevel.get(level);
                for (Long2ObjectMap.Entry<Set<NETWORK>> networksInChunk : networksByChunk.entrySet()) {
                    if (networksInLevel == null) {
                        throw new IllegalStateException("Networks in chunk "
                                                        + new ChunkPos(networksInChunk.getLongKey())
                                                        + " (" + networksByChunk.size() + " entries) are not in level "
                                                        + level);
                    } else if (!networksInLevel.containsAll(networksInChunk.getValue())) {
                        long chunkPosLong = networksInChunk.getLongKey();
                        throw new IllegalStateException("Networks in chunk "
                                                        + new ChunkPos(chunkPosLong)
                                                        + " are not in level "
                                                        + level);
                    }
                }
            }

            for (Map.Entry<LEVEL, BlockPosMap<NETWORK>> entry : networksByLevelBlockPos.entrySet()) {
                LEVEL level = entry.getKey();
                BlockPosMap<NETWORK> networksByBlockPos = entry.getValue();
                for (Long2ObjectMap.Entry<NETWORK> posEntry : networksByBlockPos.long2ObjectEntrySet()) {
                    BlockPos blockPos = BlockPos.of(posEntry.getLongKey());
                    NETWORK network = posEntry.getValue();
                    if (!Objects.requireNonNull(networksByLevelChunk.get(level).get(blockPos)).contains(network)) {
                        throw new IllegalStateException("Network "
                                                        + network
                                                        + " is not in chunk "
                                                        + new ChunkPos(blockPos));
                    }
                    if (!networksByLevel.get(level).contains(network)) {
                        throw new IllegalStateException("Network " + network + " is not in level " + level);
                    }
                }
            }
        } catch (IllegalStateException e) {
            SFM.LOGGER.error("BlockNetworkManager inconsistency detected");
            printDebugInfo();
            throw e;
        }
    }

    public void clearLevel(LEVEL level) {

        networksByLevelBlockPos.remove(level);
        networksByLevelChunk.remove(level);
        networksByLevel.remove(level);
        onChange();
    }

    public void clear() {

        networksByLevelBlockPos.clear();
        networksByLevelChunk.clear();
        networksByLevel.clear();
        onChange();
    }

    public void printDebugInfo() {

        SFM.LOGGER.info("=== BlockNetworkManager Debug Info ===");

        SFM.LOGGER.info("Networks by Level:");
        for (Map.Entry<LEVEL, Set<NETWORK>> entry : networksByLevel.entrySet()) {
            LEVEL level = entry.getKey();
            Set<NETWORK> networks = entry.getValue();
            SFM.LOGGER.info("  Level {}: {} networks", level, networks.size());
            int i = 0;
            for (NETWORK network : networks) {
                SFM.LOGGER.info(
                        "    Network {} @ {}: {} members",
                        i,
                        Integer.toHexString(System.identityHashCode(network)),
                        network.members().size()
                );
                int iMember = 0;
                for (BlockPos blockPos : network.members().keysAsBlockPosSet()) {
                    SFM.LOGGER.info("      Member {}: {}", iMember, blockPos);
                }
                i++;
            }
        }

        SFM.LOGGER.info("Networks by Level Position:");
        for (Map.Entry<LEVEL, BlockPosMap<NETWORK>> entry : networksByLevelBlockPos.entrySet()) {
            LEVEL level = entry.getKey();
            BlockPosMap<NETWORK> positionMap = entry.getValue();
            SFM.LOGGER.info("  Level {}: {} tracked positions", level, positionMap.size());
        }

        SFM.LOGGER.info("Networks by Level Chunk:");
        for (Map.Entry<LEVEL, ChunkPosMap<Set<NETWORK>>> entry : networksByLevelChunk.entrySet()) {
            LEVEL level = entry.getKey();
            ChunkPosMap<Set<NETWORK>> chunkMap = entry.getValue();
            SFM.LOGGER.info("  Level {}: {} chunks", level, chunkMap.size());
            for (Long2ObjectMap.Entry<Set<NETWORK>> chunkEntry : chunkMap.entrySet()) {
                ChunkPos chunkPos = new ChunkPos(chunkEntry.getLongKey());
                Set<NETWORK> networksInChunk = chunkEntry.getValue();
                SFM.LOGGER.info("    Chunk [{}, {}]: {} networks", chunkPos.x, chunkPos.z, networksInChunk.size());
                int i = 0;
                for (NETWORK network : networksInChunk) {
                    SFM.LOGGER.info(
                            "      Network {} @ {}: {} members",
                            i,
                            Integer.toHexString(System.identityHashCode(network)),
                            network.members().size()
                    );
                    i++;
                }
            }
        }

        SFM.LOGGER.info("=== End BlockNetworkManager Debug Info ===");
    }

    public boolean isEmpty() {

        if (SFMEnvironmentUtils.isInIDE()) {
            return networksByLevelBlockPos.isEmpty();
        } else {
            boolean a = networksByLevelBlockPos.isEmpty();
            boolean b = networksByLevelChunk.isEmpty();
            boolean c = networksByLevel.isEmpty();
            if (a != b || b != c) {
                throw new IllegalStateException("BlockNetworkManager inconsistency");
            }
            return a;
        }

    }

    public boolean containsLevel(LEVEL testLevel) {

        return networksByLevel.containsKey(testLevel);
    }

    public int networkCount() {

        int rtn = 0;
        for (Map.Entry<LEVEL, Set<NETWORK>> entry : networksByLevel.entrySet()) {
            rtn += entry.getValue().size();
        }
        return rtn;
    }

    /// Remove a network from all tracking structures.
    /// Used when clearing a large network for lazy rebuild.
    public void untrackNetwork(NETWORK network) {

        LEVEL level = network.level();

        // Remove all position lookups for this network
        BlockPosMap<NETWORK> networksByBlockPos = networksByLevelBlockPos.get(level);
        if (networksByBlockPos != null) {
            networksByBlockPos.removeBlockPositions(network.members().keysAsLongSet());
            if (networksByBlockPos.isEmpty()) {
                networksByLevelBlockPos.remove(level);
            }
        }

        // Remove from chunk lookups
        ChunkPosMap<Set<NETWORK>> networksByChunkPos = networksByLevelChunk.get(level);
        if (networksByChunkPos != null) {
            for (
                    LongIterator iterator = network.memberBlockPositionsByChunk().keySet().iterator();
                    iterator.hasNext();
            ) {
                long chunkPosLong = iterator.nextLong();
                Set<NETWORK> networksInChunk = networksByChunkPos.get(chunkPosLong);
                if (networksInChunk != null) {
                    networksInChunk.remove(network);
                    if (networksInChunk.isEmpty()) {
                        networksByChunkPos.remove(chunkPosLong);
                    }
                }
            }
            if (networksByChunkPos.isEmpty()) {
                networksByLevelChunk.remove(level);
            }
        }

        // Remove from level lookup
        Set<NETWORK> networksInLevel = networksByLevel.get(level);
        if (networksInLevel != null) {
            networksInLevel.remove(network);
            if (networksInLevel.isEmpty()) {
                networksByLevel.remove(level);
            }
        }
        onChange();
    }

    private void printChangeDiagnostics() {

        boolean enabled = false;
        if (!enabled) return;
        if (!SFMEnvironmentUtils.isInIDE()) return;
        SFM.LOGGER.info("Network lookup changed");
        SFM.LOGGER.info("NETWORKS_BY_LEVEL:");
        for (Map.Entry<LEVEL, Set<NETWORK>> entry : networksByLevel.entrySet()) {
            LEVEL level = entry.getKey();
            Set<NETWORK> networks = entry.getValue();
            SFM.LOGGER.debug("Level {} has {} networks", level, networks.size());
            StringBuilder builder = new StringBuilder();
            for (NETWORK network : networks) {
                builder.append(network.members().size()).append(" members; ");
            }
            SFM.LOGGER.debug(builder.toString());
        }
        SFM.LOGGER.info("NETWORKS_BY_CABLE_POSITION:");
        for (Map.Entry<LEVEL, BlockPosMap<NETWORK>> entry : networksByLevelBlockPos.entrySet()) {
            LEVEL level = entry.getKey();
            BlockPosMap<NETWORK> networksByCablePosition = entry.getValue();
            SFM.LOGGER.debug("Level {} has {} cables", level, networksByCablePosition.size());
        }
    }

    private void trackMemberBlockPosForNetwork(
            BlockPos memberBlockPos,
            NETWORK network
    ) {

        // Get the level
        LEVEL level = network.level();

        // Update the position lookup
        BlockPosMap<NETWORK> networksByBlockPos = networksByLevelBlockPos.computeIfAbsent(
                level,
                k -> new BlockPosMap<>()
        );
        networksByBlockPos.put(memberBlockPos, network);

        // Update the chunk lookup
        ChunkPosMap<Set<NETWORK>> networksByChunkPos = networksByLevelChunk.computeIfAbsent(
                level,
                k -> new ChunkPosMap<>()
        );
        networksByChunkPos.computeIfAbsent(new ChunkPos(memberBlockPos), k -> Sets.newIdentityHashSet()).add(network);

        // Update the level lookup
        networksByLevel.computeIfAbsent(level, k -> Sets.newIdentityHashSet()).add(network);
    }

    private void trackNetworkTransfer(
            NETWORK oldNetwork,
            NETWORK newNetwork
    ) {
        // Get the level
        LEVEL level = oldNetwork.level();
        if (newNetwork.level() != level)
            throw new IllegalStateException("Cannot transfer network ownership across levels");

        // Update the position lookup based on the NEW network's members
        // (not the old network, since during splits the new network only has a subset of members)
        BlockPosMap<NETWORK> networksByLevelBlockPosition = networksByLevelBlockPos.computeIfAbsent(
                level,
                k -> new BlockPosMap<>()
        );
        LongSet newNetworkMemberBlockPositions = newNetwork.members().keysAsLongSet();
        LongIterator newNetworkMemberBlockPositionIterator = newNetworkMemberBlockPositions.longIterator();
        while (newNetworkMemberBlockPositionIterator.hasNext()) {
            long newNetworkMemberBlockPos = newNetworkMemberBlockPositionIterator.nextLong();
            // Clobber the old entries
            networksByLevelBlockPosition.put(newNetworkMemberBlockPos, newNetwork);
        }

        // Update the chunk lookup based on the NEW network's chunk positions
        ChunkPosMap<Set<NETWORK>> networksByLevelChunk = this.networksByLevelChunk.computeIfAbsent(
                level,
                k -> new ChunkPosMap<>()
        );
        ChunkPosMap<BlockPosSet> newNetworkMemberBlockPositionsByChunk = newNetwork.memberBlockPositionsByChunk();
        for (LongIterator iterator = newNetworkMemberBlockPositionsByChunk.keySet().iterator(); iterator.hasNext(); ) {
            long chunkPosLong = iterator.nextLong();
            Set<NETWORK> networksInChunk = networksByLevelChunk.computeIfAbsent(
                    chunkPosLong,
                    k -> Sets.newIdentityHashSet()
            );
            // Remove the old network from this chunk (it may or may not be present)
            networksInChunk.remove(oldNetwork);
            // Add the new network to this chunk
            networksInChunk.add(newNetwork);
        }

        // Also need to remove the old network from any chunks it was in but the new network is not
        ChunkPosMap<BlockPosSet> oldNetworkMemberBlockPositionsByChunk = oldNetwork.memberBlockPositionsByChunk();
        for (LongIterator iterator = oldNetworkMemberBlockPositionsByChunk.keySet().iterator(); iterator.hasNext(); ) {
            long chunkPosLong = iterator.nextLong();
            // Only process chunks that the new network doesn't occupy
            if (newNetworkMemberBlockPositionsByChunk.containsKey(chunkPosLong)) continue;
            Set<NETWORK> networksInChunk = networksByLevelChunk.get(chunkPosLong);
            if (networksInChunk != null) {
                networksInChunk.remove(oldNetwork);
                if (networksInChunk.isEmpty()) {
                    networksByLevelChunk.remove(chunkPosLong);
                }
            }
        }

        // Update the level lookup
        Set<NETWORK> networksInLevel = this.networksByLevel.computeIfAbsent(
                level,
                k -> Sets.newIdentityHashSet()
        );
        // Remove the old network
        networksInLevel.remove(oldNetwork);
        // Add the new network
        networksInLevel.add(newNetwork);
    }

    /// Remove the lookup table entries for the given position.
    /// This DOES NOT perform network splitting!
    private void untrackMemberFromNetwork(
            BlockPos memberBlockPos,
            NETWORK network
    ) {

        LEVEL level = network.level();
        ChunkPos memberChunkPos = new ChunkPos(memberBlockPos);

        // Remove the member from the network
        network.removeMember(memberBlockPos);

        // Remove the block pos from the position lookup
        BlockPosMap<NETWORK> networksByBlockPos = networksByLevelBlockPos.get(level);
        if (networksByBlockPos != null) {
            networksByBlockPos.remove(memberBlockPos);
            if (networksByBlockPos.isEmpty()) {
                networksByLevelBlockPos.remove(level);
            }
        }

        // Check if the network still uses the chunk
        if (!network.usesChunk(memberChunkPos)) {
            // Remove the chunk pos from the chunk pos lookup
            ChunkPosMap<Set<NETWORK>> networksByChunkPos = networksByLevelChunk.get(level);
            if (networksByChunkPos != null) {
                Set<NETWORK> listOfNetworksInChunk = networksByChunkPos.get(memberChunkPos);
                if (listOfNetworksInChunk != null) {
                    listOfNetworksInChunk.remove(network);
                    if (listOfNetworksInChunk.isEmpty()) {
                        networksByChunkPos.remove(memberChunkPos);
                    }
                }
                if (networksByChunkPos.isEmpty()) {
                    networksByLevelChunk.remove(level);
                }
            }
        }

        // Remove the network from the level if it is now empty
        if (network.isEmpty()) {
            Set<NETWORK> networksInLevel = networksByLevel.get(level);
            if (networksInLevel != null) {
                networksInLevel.remove(network);
                if (networksInLevel.isEmpty()) {
                    networksByLevel.remove(level);
                }
            }
        }

    }

}
