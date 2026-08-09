package ca.teamdman.sfm.common.block_network;

import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.BlockPosMap;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfm.common.util.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class to memorize the relevant chains of inventory cables.
 * <p>
 * Rather than looking up the connected cable blocks for each manager each tick,
 * this class aims to keep track of the chains instead.
 * Adding or removing cable blocks that invoke the relevant methods for this class
 * will help build the network.
 * <p>
 * Adding cables can do one of:
 * - append to existing network
 * - cause two existing networks to join
 * - create a new network
 * <p>
 * Removing cables can:
 * - Remove it from the network
 * - Remove the network if it was the only member
 * - Cause a network to split into other networks if it was a "bridge" block
 */
public class CableNetworkManager {
    private static final BlockNetworkManager<Level, Unit, CableNetwork> NETWORK_MANAGER = new BlockNetworkManager<>(
            CableNetwork::cableMemberFilterMapper,
            CableNetwork::new
    );

    private static final Set<CableNetwork> NETWORKS_WITH_PENDING_NOTIFICATIONS = ConcurrentHashMap.newKeySet();

    public static Optional<CableNetwork> getOrRegisterNetworkFromManagerPosition(ManagerBlockEntity tile) {
        Level level = tile.getLevel();
        assert level != null;
        return getOrRegisterNetworkFromCablePosition(level, tile.getBlockPos());
    }

    public static Stream<CableNetwork> getNetworksInRange(Level level, BlockPos pos, double maxDistance) {
        if (level.isClientSide()) return Stream.empty();
        return NETWORK_MANAGER.getNetworksForLevel(level).values().stream()
                // .distinct()
                .filter(net -> net
                        .getCablePositions()
                        .stream()
                        .anyMatch(cablePos -> cablePos.distSqr(pos) < maxDistance * maxDistance));
    }

    public static void unregisterNetworkForTestingPurposes(CableNetwork network) {
        NETWORK_MANAGER.untrackNetwork(network);
    }

    public static BlockPosMap<CableNetwork> getNetworksForLevel(Level level) {
        return NETWORK_MANAGER.getNetworksForLevel(level);
    }

    public static void onCablePlaced(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        CableNetwork network = NETWORK_MANAGER.onMemberAddedToLevel(level, pos);
        if (network != null) {
            // Invalidate and rebuild auto labels to discover newly connected blocks
            // Uses delayed notification to batch rapid changes
            network.invalidateAutoLabelsAndNotifyDependents();
        }
    }

    public static void onCableRemoved(Level level, BlockPos cablePos) {
        if (level.isClientSide()) return;
        List<CableNetwork> resultingNetworks = NETWORK_MANAGER.onMemberRemovedFromLevel(level, cablePos);

        // Notify all remaining networks to recompile their dependents
        // (network topology changed, libraries may have become inaccessible)
        // Uses delayed notification to batch rapid changes
        for (CableNetwork remainingNetwork : resultingNetworks) {
            remainingNetwork.invalidateAutoLabelsAndNotifyDependents();
        }

        // Always notify blocks adjacent to the removed cable, after network changes are complete
        // This handles both networked and standalone cables
        notifyBlocksAdjacentToRemovedCable(level, cablePos);
    }

    /**
     * Notifies libraries and managers directly adjacent to a removed cable.
     * Called regardless of whether a network existed, to handle standalone cables.
     */
    private static void notifyBlocksAdjacentToRemovedCable(Level level, BlockPos cablePos) {
        BlockPos.MutableBlockPos adjacentPos = new BlockPos.MutableBlockPos();
        for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            adjacentPos.set(cablePos).move(direction);
            if (level.getBlockEntity(adjacentPos) instanceof LibraryBlockEntity) {
                // Check if this library is still connected to a meaningful network
                // (one with other cables besides just the library itself)
                boolean stillOnMeaningfulNetwork = NETWORK_MANAGER.getNetworksForLevel(level)
                        .values().stream()
                        .anyMatch(net -> net.containsCablePosition(adjacentPos) && net.getCableCount() > 1);
                if (!stillOnMeaningfulNetwork) {
                    // Library is isolated - notify via its own network (batched) to recompile
                    getOrRegisterNetworkFromCablePosition(level, adjacentPos.immutable())
                            .ifPresent(CableNetwork::invalidateAutoLabelsAndNotifyDependents);
                }
            } else if (level.getBlockEntity(adjacentPos) instanceof ManagerBlockEntity) {
                // Check if this manager is still connected to a meaningful network
                // (one with other cables besides just the manager itself)
                boolean stillOnMeaningfulNetwork = NETWORK_MANAGER.getNetworksForLevel(level)
                        .values().stream()
                        .anyMatch(net -> net.containsCablePosition(adjacentPos) && net.getCableCount() > 1);
                if (!stillOnMeaningfulNetwork) {
                    // Manager is isolated - notify via its own network (batched) to rebuild
                    getOrRegisterNetworkFromCablePosition(level, adjacentPos.immutable())
                            .ifPresent(CableNetwork::invalidateAutoLabelsAndNotifyDependents);
                }
            }
        }
    }

    public static void purgeCableNetworkForManager(ManagerBlockEntity manager) {
        Level level = manager.getLevel();
        if (level == null) return;
        CableNetwork network = NETWORK_MANAGER.getNetwork(level, manager.getBlockPos());
        if (network != null) {
            NETWORK_MANAGER.untrackNetwork(network);
        }
    }

    /// Gets the cable network object. If none exists and one should, it will create and populate
    /// one.
    ///
    /// Networks should only exist on the server side.
    public static Optional<CableNetwork> getOrRegisterNetworkFromCablePosition(Level level, BlockPos pos) {
        if (level.isClientSide()) return Optional.empty();

        CableNetwork network = NETWORK_MANAGER.onMemberAddedToLevel(level, pos);
        return Optional.ofNullable(network);
    }

    /**
     * Finds the currently tracked cable network without constructing or modifying one.
     */
    public static Optional<CableNetwork> getNetworkFromCablePosition(Level level, BlockPos pos) {
        if (level.isClientSide()) return Optional.empty();
        return Optional.ofNullable(NETWORK_MANAGER.getNetwork(level, pos));
    }

    public static List<BlockPos> getBadCableCachePositions(Level level) {

        return NETWORK_MANAGER.getNetworksForLevel(level)
                .values()
                .stream()
                .flatMap(network -> network.getCablePositions().stream())
                .filter(pos -> !(level.getBlockState(pos).getBlock() instanceof ICableBlock))
                .collect(Collectors.toList());
    }

    public static void clear() {
        NETWORK_MANAGER.clear();
        NETWORKS_WITH_PENDING_NOTIFICATIONS.clear();
    }

    @SFMSubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var chunk = event.getChunk();
        NETWORK_MANAGER.purgeChunk(level, chunk.getPos());
    }

    @SFMSubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        NETWORK_MANAGER.clearLevel(level);
        // Remove any pending notifications for this level
        NETWORKS_WITH_PENDING_NOTIFICATIONS.removeIf(net -> net.getLevel() == level);
    }

    @SFMSubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        processPendingNotifications();
    }

    /**
     * Registers a network for delayed notification processing.
     * Called by CableNetwork when a notification is scheduled.
     */
    public static void schedulePendingNotification(CableNetwork network) {
        NETWORKS_WITH_PENDING_NOTIFICATIONS.add(network);
    }

    /**
     * Processes all networks with pending notifications that are ready to fire.
     */
    private static void processPendingNotifications() {
        if (NETWORKS_WITH_PENDING_NOTIFICATIONS.isEmpty()) return;

        Iterator<CableNetwork> iterator = NETWORKS_WITH_PENDING_NOTIFICATIONS.iterator();
        while (iterator.hasNext()) {
            CableNetwork network = iterator.next();
            long pendingTick = network.getPendingNotificationTick();
            if (pendingTick < 0) {
                // No longer pending
                iterator.remove();
                continue;
            }
            long currentTick = network.getLevel().getGameTime();
            if (currentTick >= pendingTick) {
                network.processPendingNotification();
                iterator.remove();
            }
        }
    }
}
