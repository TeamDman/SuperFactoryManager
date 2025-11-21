package ca.teamdman.sfm.common.cablenetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.NotStored;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfm.common.util.SFMStreamUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import vswe.superfactory.blocks.BlockManager;
import vswe.superfactory.tiles.TileEntityManager;

/// When a {@link ManagerBlockEntity} is ticking many times in a row, there is worldly context that changes
/// infrequently.
/// This class stores a cache of the cables and capabilities that the manager is aware of, to avoid repeated expensive
/// lookups.
public class CableNetwork {

    protected final World level;
    protected final LongSet cablePositions = new LongOpenHashSet();
    protected final SFMBlockCapabilityCacheForLevel levelCapabilityCache = new SFMBlockCapabilityCacheForLevel();
    protected final LongSet visualManagerPositions = new LongOpenHashSet();

    public CableNetwork(World level) {
        this.level = level;
    }

    public SFMBlockCapabilityCacheForLevel getLevelCapabilityCache() {
        return levelCapabilityCache;
    }

    /**
     * Only cable blocks are valid network members
     */
    public static boolean isCable(
                                  @Nullable World world,
                                  @NotStored BlockPos cablePos) {
        if (world == null) return false;
        return world
                .getBlockState(cablePos)
                .getBlock() instanceof ICableBlock;
    }

    public static boolean isVisualManager(
                                          @Nullable World world,
                                          @NotStored BlockPos cablePos) {
        if (world == null) return false;
        return world.getBlockState(cablePos).getBlock() instanceof BlockManager;
    }

    public void rebuildNetwork(@NotStored BlockPos start) {
        cablePositions.clear();
        levelCapabilityCache.clear();
        discoverCables(getLevel(), start).forEach(this::addCableOrVisualManager);
    }

    public void rebuildNetworkFromCache(
                                        @NotStored BlockPos start,
                                        CableNetwork other) {
        cablePositions.clear();
        levelCapabilityCache.clear();
        visualManagerPositions.clear();

        // discover connected cables
        var cables = SFMStreamUtils.<BlockPos, BlockPos>getRecursiveStream(
                (current, next, results) -> {
                    results.accept(current);
                    BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
                    for (EnumFacing d : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                        target.setPos(current).move(d);
                        if (other.containsCablePosition(target)) {
                            next.accept(target.toImmutable());
                        }
                    }
                }, start).collect(Collectors.toList());

        // restore cable positions
        for (BlockPos cablePos : cables) {
            cablePositions.add(cablePos.toLong());
            if (isVisualManager(level, cablePos)) {
                visualManagerPositions.add(cablePos.toLong());
            }
        }

        // restore capabilities
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        LongSet seenCapabilityPositions = new LongOpenHashSet();
        for (BlockPos cablePos : cables) {
            for (EnumFacing direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                target.setPos(cablePos).move(direction);
                // the same block may be touching multiple cables in the network
                boolean firstVisit = seenCapabilityPositions.add(target.toLong());
                if (firstVisit) {
                    levelCapabilityCache.overwriteFromOther(target, other.levelCapabilityCache);
                }
            }
        }
    }

    /// This assumes that the start position is a cable block
    public static Stream<BlockPos> discoverCables(
                                                  World level,
                                                  @NotStored BlockPos startPos) {
        return SFMStreamUtils.getRecursiveStream(
                (current, next, results) -> {
                    results.accept(current);
                    BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
                    for (EnumFacing d : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                        target.setPos(current).move(d);
                        if (isCable(level, target)) {
                            next.accept(target.toImmutable());
                        }
                    }
                }, startPos);
    }

    public void addCable(@NotStored BlockPos pos) {
        cablePositions.add(pos.toLong());
    }

    public void addCableOrVisualManager(@NotStored BlockPos pos) {
        cablePositions.add(pos.toLong());
        if (isVisualManager(level, pos)) {
            visualManagerPositions.add(pos.toLong());
        }
    }

    public World getLevel() {
        return level;
    }

    @Override
    public String toString() {
        return "CableNetwork{level=" + getLevel().provider.getDimension() + ", #cables=" + getCableCount() +
                ", #cache=" + levelCapabilityCache.size() + "}";
    }

    /**
     * Cables should only join the network if they would be touching a cable already in the network
     *
     * @param pos Candidate cable position
     * @return {@code true} if adjacent to cable in network
     */
    public boolean isAdjacentToCable(@NotStored BlockPos pos) {
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        for (EnumFacing direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            target.setPos(pos).move(direction);
            if (containsCablePosition(target)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsCablePosition(@NotStored BlockPos pos) {
        return cablePositions.contains(pos.toLong());
    }

    @MCVersionDependentBehaviour
    public <CAP> @NotNull SFMBlockCapabilityResult<CAP> getCapability(
                                                                      SFMBlockCapabilityKind<CAP> capKind,
                                                                      @NotStored BlockPos pos,
                                                                      @Nullable EnumFacing direction,
                                                                      TranslatableLogger logger) {
        return SFMBlockCapabilityDiscovery.discoverCapabilityFromNetwork(
                this,
                capKind,
                pos,
                direction,
                logger);
    }

    public int getCableCount() {
        return cablePositions.size();
    }

    /**
     * Merges a network into this one, such as when a cable connects two networks
     *
     * @param other Foreign network
     */
    public void mergeNetwork(CableNetwork other) {
        cablePositions.addAll(other.cablePositions);
        visualManagerPositions.addAll(other.visualManagerPositions);
        levelCapabilityCache.putAll(other.levelCapabilityCache);
    }

    public boolean isEmpty() {
        return cablePositions.isEmpty();
    }

    public Stream<BlockPos> getCablePositions() {
        return cablePositions.stream().map(BlockPos::fromLong);
    }

    public LongSet getCablePositionsRaw() {
        return cablePositions;
    }

    public Stream<BlockPos> getVisualManagerPositions() {
        return visualManagerPositions.stream().map(BlockPos::fromLong);
    }

    public Stream<BlockPos> getCapabilityProviderPositions() {
        return levelCapabilityCache.getPositions();
    }

    public void bustCacheForChunk(Chunk chunkAccess) {
        levelCapabilityCache.bustCacheForChunk(chunkAccess);
    }

    /**
     * Discover what networks would exist if this network did not have a cable at {@code cablePos}.
     *
     * @param cablePos cable position to be removed
     * @return resulting networks to replace this network
     */
    protected List<CableNetwork> withoutCable(@NotStored BlockPos cablePos) {
        cablePositions.remove(cablePos.toLong());
        List<CableNetwork> branches = new ArrayList<>();
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        for (EnumFacing direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            target.setPos(cablePos).move(direction);
            if (!containsCablePosition(target)) continue;
            // make sure that a branch network doesn't already contain this cable
            if (branches.stream().anyMatch(n -> n.containsCablePosition(target))) continue;
            var branchNetwork = new CableNetwork(this.getLevel());
            branchNetwork.rebuildNetworkFromCache(target, this);
            branches.add(branchNetwork);
        }
        return branches;
    }

    public void updateVisualManagers() {
        this.updateVisualManagers(null);
    }

    public void updateVisualManagers(@Nullable BlockPos cablePos) {
        this.getVisualManagerPositions().forEach((pos) -> {
            if (!pos.equals(cablePos) && this.level.getTileEntity(pos) instanceof TileEntityManager manager) {
                manager.updateInventories();
            }
        });
    }

    public void addVisualManager(BlockPos pos) {
        this.visualManagerPositions.add(pos.toLong());
    }
}
