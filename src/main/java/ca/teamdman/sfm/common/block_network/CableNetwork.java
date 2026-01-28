package ca.teamdman.sfm.common.block_network;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/// A cable network extends {@link BlockNetwork} to add capability caching for blocks adjacent to cables.
/// When a {@link ManagerBlockEntity} is ticking many times in a row, there is worldly context that changes infrequently.
/// This class stores a cache of the cables and capabilities that the manager is aware of, to avoid repeated expensive lookups.
public class CableNetwork extends BlockNetwork<Level, Unit> {
    protected final SFMBlockCapabilityCacheForLevel levelCapabilityCache;

    public CableNetwork(
            Level level,
            BlockNetworkMemberFilterMapper<Level, Unit> memberFilterMapper
    ) {
        super(level, memberFilterMapper, CableNetwork::new);
        this.levelCapabilityCache = new SFMBlockCapabilityCacheForLevel(level);
    }

    public SFMBlockCapabilityCacheForLevel getLevelCapabilityCache() {
        return levelCapabilityCache;
    }

    /**
     * Only cable blocks are valid network members
     */
    public static boolean isCable(
            @Nullable Level world,
            BlockPos cablePos
    ) {
        if (world == null) return false;
        return world
                .getBlockState(cablePos)
                .getBlock() instanceof ICableBlock;
    }

    /// Member filter mapper for use with BlockNetworkManager
    public static @Nullable Unit cableMemberFilterMapper(Level level, BlockPos pos) {
        return isCable(level, pos) ? Unit.INSTANCE : null;
    }

    /// Discover all contiguous cable positions starting from the given position.
    /// This assumes that the start position is a cable block.
    public static Stream<BlockPos> discoverCables(
            Level level,
            BlockPos startPos
    ) {
        return SFMStreamUtils.getRecursiveStream(
                (current, next, results) -> {
                    results.accept(current);
                    BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
                    for (Direction d : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                        target.set(current).move(d);
                        if (isCable(level, target)) {
                            next.accept(target.immutable());
                        }
                    }
                }, startPos
        );
    }

    public Level getLevel() {
        return level();
    }

    @Override
    public String toString() {
        return "CableNetwork{level="
               + getLevel().dimension().location()
               + ", #cables="
               + getCableCount()
               + ", #cache="
               + levelCapabilityCache.size()
               + "}";
    }

    /**
     * Cables should only join the network if they would be touching a cable already in the network
     *
     * @param pos Candidate cable position
     * @return {@code true} if adjacent to cable in network
     */
    public boolean isAdjacentToCable(BlockPos pos) {
        if (containsCablePosition(pos)) {
            return true; // allow managers to interact with themselves
        }
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            target.set(pos).move(direction);
            if (containsCablePosition(target)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsCablePosition(BlockPos pos) {
        return members().containsKey(pos);
    }

    @MCVersionDependentBehaviour
    public <CAP> @NotNull SFMBlockCapabilityResult<CAP> getCapability(
            SFMBlockCapabilityKind<CAP> capKind,
            BlockPos pos,
            @Nullable Direction direction,
            TranslatableLogger logger
    ) {
       return SFMBlockCapabilityDiscovery.discoverCapabilityFromNetwork(
               this,
               capKind,
               pos,
               direction,
               logger
       );
    }

    public int getCableCount() {
        return size();
    }

    public Stream<BlockPos> getCablePositions() {
        return members().keysAsLongSet().longStream().mapToObj(BlockPos::of);
    }

    public Stream<BlockPos> getCapabilityProviderPositions() {
        return levelCapabilityCache.getPositions();
    }

    @Override
    void purgeChunk(ChunkPos chunkPos) {
        levelCapabilityCache.bustCacheForChunk(chunkPos);
        super.purgeChunk(chunkPos);
    }

    @Override
    void addAllFromOtherNetwork(BlockNetwork<Level, Unit> other) {
        super.addAllFromOtherNetwork(other);
        // Also, merge capability caches if the other network is a CableNetwork
        if (other instanceof CableNetwork otherCable) {
            levelCapabilityCache.putAll(otherCable.levelCapabilityCache);
        }
    }

    @Override
    List<BlockNetwork<Level, Unit>> splitRemoveMember(BlockPos blockPos) {
        // Call the parent implementation to handle the position tracking split
        List<BlockNetwork<Level, Unit>> branches = super.splitRemoveMember(blockPos);

        // Transfer capability cache entries to the appropriate branch networks
        for (BlockNetwork<Level, Unit> branch : branches) {
            if (branch instanceof CableNetwork cableBranch) {
                transferCapabilityCacheToBranch(cableBranch);
            }
        }

        return branches;
    }

    /// Transfer capability cache entries from this network to a branch network.
    /// Only transfers entries for positions adjacent to cables in the branch network.
    private void transferCapabilityCacheToBranch(CableNetwork branch) {
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        BlockPosSet seenCapabilityPositions = new BlockPosSet();

        // For each cable in the branch, check adjacent positions for capability cache entries
        for (BlockPos cablePos : branch.members().keysAsBlockPosSet()) {
            for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                target.set(cablePos).move(direction);
                // The same block may be touching multiple cables in the network
                boolean firstVisit = seenCapabilityPositions.add(target);
                if (firstVisit) {
                    branch.levelCapabilityCache.overwriteFromOther(target, this.levelCapabilityCache);
                }
            }
        }
    }
}
