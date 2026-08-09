package ca.teamdman.sfm.common.block_network;

import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.program_builder.LibraryDefinitions;
import ca.teamdman.sfml.program_builder.LibraryResolver;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.util.*;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/// A cable network extends {@link BlockNetwork} to add capability caching for blocks adjacent to cables.
/// When a {@link ManagerBlockEntity} is ticking many times in a row, there is worldly context that changes infrequently.
/// This class stores a cache of the cables and capabilities that the manager is aware of, to avoid repeated expensive lookups.
public class CableNetwork extends BlockNetwork<Level, Unit> {
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
    public static @Nullable Unit cableMemberFilterMapper(
            Level level,
            BlockPos pos
    ) {

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

    public BlockPosIterator getCapabilityProviderPositions() {

        return levelCapabilityCache.getPositions();
    }
    public LongSet getCapabilityProviderPositionsRaw() {
        return levelCapabilityCache.getPositionsRaw();
    }


    public BlockPosIterator getCablePositions() {

        return members().positions();
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
        BlockPosSet visitedAdjacent = new BlockPosSet();
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        Level level = getLevel();

        for (BlockPos cablePos : getCablePositions()) {
            // Check if the cable itself is a manager
            if (level.getBlockEntity(cablePos) instanceof ManagerBlockEntity) {
                autoLabelCache.add(ManagerBlockEntity.MANAGER_LABEL, cablePos.immutable());
            }

            // Check if the cable itself is a library (LibraryBlock implements ICableBlock)
            if (level.getBlockEntity(cablePos) instanceof LibraryBlockEntity) {
                autoLabelCache.add(LibraryBlockEntity.LIBRARY_LABEL, cablePos.immutable());
            }

            // Check adjacent positions for library blocks
            for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                target.set(cablePos).move(direction);

                // Skip if already visited
                if (!visitedAdjacent.add(target)) {
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

    public LongSet getCablePositionsRaw() {
        return members().keySet();
    }

    /**
     * Returns the currently loaded managers that are members of this cable network.
     *
     * The ordering is deterministic so external integrations can present a stable list without
     * selecting an arbitrary manager from a multi-manager network.
     */
    public Stream<ManagerBlockEntity> getManagers() {

        return getCablePositions()
                .stream()
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingLong(BlockPos::asLong))
                .map(getLevel()::getBlockEntity)
                .filter(ManagerBlockEntity.class::isInstance)
                .map(ManagerBlockEntity.class::cast);
    }

    @Override
    void purgeChunk(ChunkPos chunkPos) {

        levelCapabilityCache.bustCacheForChunk(chunkPos);
        autoLabelCache = null;
        super.purgeChunk(chunkPos);
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
        long currentTick = getLevel().getGameTime();
        pendingNotificationTick = currentTick + NOTIFICATION_DELAY_TICKS;

        // Register this network for delayed processing
        CableNetworkManager.schedulePendingNotification(this);
    }

    /**
     * Called by CableNetworkManager when the pending notification delay has elapsed.
     * Sends notifications to all managers and libraries on the network.
     */
    public void processPendingNotification() {
        long currentTick = getLevel().getGameTime();
        if (pendingNotificationTick < 0 || currentTick < pendingNotificationTick) {
            // Not yet time, or no pending notification
            return;
        }

        // Clear pending state
        pendingNotificationTick = -1;

        // Get current positions (cache was already invalidated)
        BlockPosSet managerPositions = getOrRebuildAutoLabels()
                .getPositions(ManagerBlockEntity.MANAGER_LABEL);
        BlockPosSet libraryPositions = getOrRebuildAutoLabels()
                .getPositions(LibraryBlockEntity.LIBRARY_LABEL);

        Level level = getLevel();

        // Notify all managers to re-validate their programs
        for (BlockPos pos : managerPositions.blockPosIterator()) {
            if (level.getBlockEntity(pos) instanceof ManagerBlockEntity manager) {
                manager.rebuildProgramAndUpdateDisk();
            }
        }

        // Notify all library blocks to recompile their disks
        for (BlockPos pos : libraryPositions.blockPosIterator()) {
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
            BlockPosSet libraryPositions = getOrRebuildAutoLabels()
                    .getPositions(LibraryBlockEntity.LIBRARY_LABEL);

            Level level = getLevel();

            // Track this library to detect circular dependencies
            librariesBeingResolved.add(libraryName);
            try {
                // O(N) scan of libraries where N is the number of library blocks (typically small)
                for (BlockPos pos : libraryPositions.blockPosIterator()) {
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

    /// Transfer capability cache entries from this network to a branch network.
    /// Only transfers entries for positions adjacent to cables in the branch network.
    private void transferCapabilityCacheToBranch(CableNetwork branch) {

        BlockPosSet seenCapabilityPositions = new BlockPosSet();

        // For each cable in the branch, check adjacent positions for capability cache entries
        BlockPos.MutableBlockPos neighbourPos = new BlockPos.MutableBlockPos();
        for (BlockPos.MutableBlockPos cablePos : branch.members().positions()) {
            for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                neighbourPos.set(cablePos);
                neighbourPos.move(direction);
                // The same block may be touching multiple cables in the network
                boolean firstVisit = seenCapabilityPositions.add(neighbourPos); // correctness: consuming function makes immutable
                if (firstVisit) {
                    branch.levelCapabilityCache.overwriteFromOther(
                            neighbourPos,
                            this.levelCapabilityCache
                    ); // correctness: consuming function makes immutable
                }
            }
        }
    }

}
