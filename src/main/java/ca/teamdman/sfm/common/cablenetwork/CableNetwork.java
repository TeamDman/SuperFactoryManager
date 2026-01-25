package ca.teamdman.sfm.common.cablenetwork;

import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.program_builder.LibraryDefinitions;
import ca.teamdman.sfml.program_builder.LibraryResolver;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfm.common.util.SFMStreamUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/// When a {@link ManagerBlockEntity} is ticking many times in a row, there is worldly context that changes infrequently.
/// This class stores a cache of the cables and capabilities that the manager is aware of, to avoid repeated expensive lookups.
public class CableNetwork {
    protected final Level level;
    protected final LongSet cablePositions = new LongOpenHashSet();
    protected final SFMBlockCapabilityCacheForLevel levelCapabilityCache;

    /**
     * Cached label positions for auto-discovered blocks (e.g., library blocks).
     * This is populated lazily and cleared when the network is rebuilt.
     */
    private @Nullable LabelPositionHolder autoLabelCache = null;

    /**
     * Tracks the tick when a delayed notification should fire.
     * Set to -1 when no notification is pending.
     */
    private long pendingNotificationTick = -1;

    /**
     * Delay in ticks before a batched notification fires.
     */
    private static final int NOTIFICATION_DELAY_TICKS = 5;

    public CableNetwork(Level level) {
        this.level = level;
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

    public void rebuildNetwork(BlockPos start) {
        cablePositions.clear();
        levelCapabilityCache.clear();
        autoLabelCache = null;
        discoverCables(getLevel(), start).forEach(this::addCable);
    }

    public void rebuildNetworkFromCache(
            BlockPos start,
            CableNetwork other
    ) {
        cablePositions.clear();
        levelCapabilityCache.clear();
        autoLabelCache = null;

        // discover connected cables
        var cables = SFMStreamUtils.<BlockPos, BlockPos>getRecursiveStream(
                (current, next, results) -> {
                    results.accept(current);
                    BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
                    for (Direction d : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                        target.set(current).move(d);
                        if (other.containsCablePosition(target)) {
                            next.accept(target.immutable());
                        }
                    }
                }, start
        ).toList();

        // restore cable positions
        for (BlockPos cablePos : cables) {
            cablePositions.add(cablePos.asLong());
        }

        // restore capabilities
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        LongSet seenCapabilityPositions = new LongOpenHashSet();
        for (BlockPos cablePos : cables) {
            for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                target.set(cablePos).move(direction);
                // the same block may be touching multiple cables in the network
                boolean firstVisit = seenCapabilityPositions.add(target.asLong());
                if (firstVisit) {
                    levelCapabilityCache.overwriteFromOther(target, other.levelCapabilityCache);
                }
            }
        }
    }

    /// This assumes that the start position is a cable block
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

    public void addCable(BlockPos pos) {
        cablePositions.add(pos.asLong());
    }

    public Level getLevel() {
        return level;
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
        return cablePositions.contains(pos.asLong());
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
        return cablePositions.size();
    }

    /**
     * Merges a network into this one, such as when a cable connects two networks
     *
     * @param other Foreign network
     */
    public void mergeNetwork(CableNetwork other) {
        cablePositions.addAll(other.cablePositions);
        levelCapabilityCache.putAll(other.levelCapabilityCache);
    }

    public boolean isEmpty() {
        return cablePositions.isEmpty();
    }

    public Stream<BlockPos> getCablePositions() {
        return cablePositions.longStream().mapToObj(BlockPos::of);
    }

    public LongSet getCablePositionsRaw() {
        return cablePositions;
    }

    public Stream<BlockPos> getCapabilityProviderPositions() {
        return levelCapabilityCache.getPositions();
    }

    /**
     * Gets or rebuilds the auto-discovered label cache.
     * This cache contains positions of special blocks on the network:
     * - Manager blocks (cables): labeled with {@link ManagerBlockEntity#MANAGER_LABEL}
     * - Library blocks (adjacent to cables): labeled with {@link LibraryBlockEntity#LIBRARY_LABEL}
     *
     * @return the auto-discovered label position holder
     */
    public LabelPositionHolder getOrRebuildAutoLabels() {
        if (autoLabelCache != null) {
            return autoLabelCache;
        }

        autoLabelCache = LabelPositionHolder.empty();

        // Discover managers and libraries (which can be cables themselves) and adjacent blocks
        LongSet visitedAdjacent = new LongOpenHashSet();
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();

        for (long cablePosLong : cablePositions) {
            BlockPos cablePos = BlockPos.of(cablePosLong);

            // Check if the cable itself is a manager
            if (level.getBlockEntity(cablePos) instanceof ManagerBlockEntity) {
                autoLabelCache.add(ManagerBlockEntity.MANAGER_LABEL, cablePos);
            }

            // Check if the cable itself is a library (LibraryBlock implements ICableBlock)
            if (level.getBlockEntity(cablePos) instanceof LibraryBlockEntity) {
                autoLabelCache.add(LibraryBlockEntity.LIBRARY_LABEL, cablePos);
            }

            // Check adjacent positions for library blocks
            for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                target.set(cablePos).move(direction);
                long targetLong = target.asLong();

                // Skip if already visited
                if (!visitedAdjacent.add(targetLong)) {
                    continue;
                }

                // Check if this is a library block
                if (level.getBlockEntity(target) instanceof LibraryBlockEntity) {
                    autoLabelCache.add(LibraryBlockEntity.LIBRARY_LABEL, target.immutable());
                }
            }
        }

        return autoLabelCache;
    }

    public void bustCacheForChunk(ChunkAccess chunkAccess) {
        levelCapabilityCache.bustCacheForChunk(chunkAccess);
        autoLabelCache = null;
    }

    /**
     * Invalidates the auto-label cache without notifying managers.
     * Use this when you need to capture manager positions before a change,
     * then notify them manually after the change is complete.
     */
    public void invalidateAutoLabelCache() {
        autoLabelCache = null;
    }

    /**
     * Invalidates the auto-label cache and schedules a delayed notification to all
     * managers and library blocks on this network. Multiple rapid calls will reset
     * the delay, ensuring only the final state is processed.
     */
    public void invalidateAutoLabelsAndNotifyDependents() {
        // Invalidate the cache immediately
        autoLabelCache = null;

        // Schedule notification after delay (resets if called again)
        long currentTick = level.getGameTime();
        pendingNotificationTick = currentTick + NOTIFICATION_DELAY_TICKS;

        // Register this network for delayed processing
        CableNetworkManager.schedulePendingNotification(this);
    }

    /**
     * Called by CableNetworkManager when the pending notification delay has elapsed.
     * Sends notifications to all managers and libraries on the network.
     */
    public void processPendingNotification() {
        long currentTick = level.getGameTime();
        if (pendingNotificationTick < 0 || currentTick < pendingNotificationTick) {
            // Not yet time, or no pending notification
            return;
        }

        // Clear pending state
        pendingNotificationTick = -1;

        // Get current positions (cache was already invalidated)
        Set<BlockPos> managerPositions = getOrRebuildAutoLabels()
                .getPositions(ManagerBlockEntity.MANAGER_LABEL);
        Set<BlockPos> libraryPositions = getOrRebuildAutoLabels()
                .getPositions(LibraryBlockEntity.LIBRARY_LABEL);

        // Notify all managers to re-validate their programs
        for (BlockPos pos : managerPositions) {
            if (level.getBlockEntity(pos) instanceof ManagerBlockEntity manager) {
                manager.rebuildProgramAndUpdateDisk();
            }
        }

        // Notify all library blocks to recompile their disks
        for (BlockPos pos : libraryPositions) {
            if (level.getBlockEntity(pos) instanceof LibraryBlockEntity library) {
                library.recompileAllDisks();
            }
        }
    }

    /**
     * @return true if this network has a pending notification scheduled
     */
    public boolean hasPendingNotification() {
        return pendingNotificationTick >= 0;
    }

    /**
     * @return the tick when the pending notification should fire, or -1 if none
     */
    public long getPendingNotificationTick() {
        return pendingNotificationTick;
    }

    /**
     * Discover what networks would exist if this network did not have a cable at {@code cablePos}.
     *
     * @param cablePos cable position to be removed
     * @return resulting networks to replace this network
     */
    protected List<CableNetwork> withoutCable(BlockPos cablePos) {
        cablePositions.remove(cablePos.asLong());
        List<CableNetwork> branches = new ArrayList<>();
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            target.set(cablePos).move(direction);
            if (!containsCablePosition(target)) continue;
            // make sure that a branch network doesn't already contain this cable
            if (branches.stream().anyMatch(n -> n.containsCablePosition(target))) continue;
            var branchNetwork = new CableNetwork(this.getLevel());
            branchNetwork.rebuildNetworkFromCache(target, this);
            branches.add(branchNetwork);
        }
        return branches;
    }

    /**
     * Creates a library resolver that finds definitions from library blocks on this cable network.
     * Uses the auto-discovered label cache for O(1) library block lookup.
     *
     * @return A library resolver for this network
     */
    public LibraryResolver createLibraryResolver() {
        return createLibraryResolver(new HashSet<>());
    }

    /**
     * Creates a library resolver that finds definitions from library blocks on this cable network.
     * Uses the auto-discovered label cache for O(1) library block lookup.
     * Supports tracking circular dependencies across nested library resolutions.
     *
     * @param librariesBeingResolved Shared set for tracking circular dependencies across resolution calls
     * @return A library resolver for this network
     */
    public LibraryResolver createLibraryResolver(Set<String> librariesBeingResolved) {
        return libraryName -> {
            // Check for circular dependency before resolving
            if (librariesBeingResolved.contains(libraryName)) {
                throw new IllegalArgumentException("Circular library dependency detected: " + libraryName);
            }

            // O(1) lookup for all library positions via auto-discovered labels
            Set<BlockPos> libraryPositions = getOrRebuildAutoLabels()
                    .getPositions(LibraryBlockEntity.LIBRARY_LABEL);

            // Track this library to detect circular dependencies
            librariesBeingResolved.add(libraryName);
            try {
                // O(N) scan of libraries where N is the number of library blocks (typically small)
                for (BlockPos pos : libraryPositions) {
                    if (!(level.getBlockEntity(pos) instanceof LibraryBlockEntity library)) {
                        continue;
                    }

                    // Create a nested resolver that shares the circular dependency tracking
                    LibraryResolver nestedResolver = createLibraryResolver(librariesBeingResolved);
                    LibraryDefinitions defs = library.getDefinitionsForLibrary(libraryName, nestedResolver);
                    if (defs != null) {
                        return Optional.of(defs);
                    }
                }
            } finally {
                librariesBeingResolved.remove(libraryName);
            }

            return Optional.empty();
        };
    }
}
