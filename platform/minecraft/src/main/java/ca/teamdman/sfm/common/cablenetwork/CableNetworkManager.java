package ca.teamdman.sfm.common.cablenetwork;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfm.common.util.SFMStreamUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
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
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = SFM.MOD_ID)
public class CableNetworkManager {
    private static final Map<Level, Long2ObjectMap<CableNetwork>> NETWORKS_BY_CABLE_POSITION = new Object2ObjectOpenHashMap<>();
    private static final Map<Level, List<CableNetwork>> NETWORKS_BY_LEVEL = new Object2ObjectOpenHashMap<>();

    /**
     * For diagnostics, called when a lookup map has changed
     */
    private static void onNetworkLookupChanged() {
//        if (!SFMEnvironment.isInIDE()) return;
//        SFM.LOGGER.info("Network lookup changed");
//        SFM.LOGGER.info("NETWORKS_BY_LEVEL:");
//        for (Map.Entry<Level, List<CableNetwork>> entry : NETWORKS_BY_LEVEL.entrySet()) {
//            Level level = entry.getKey();
//            List<CableNetwork> networks = entry.getValue();
//            SFM.LOGGER.debug("Level {} has {} networks", level, networks.size());
//            StringBuilder builder = new StringBuilder();
//            for (CableNetwork network : networks) {
//                builder.append(network.getCableCount()).append(" cables; ");
//            }
//            SFM.LOGGER.debug(builder.toString());
//        }
//        SFM.LOGGER.info("NETWORKS_BY_CABLE_POSITION:");
//        for (Map.Entry<Level, Long2ObjectMap<CableNetwork>> entry : NETWORKS_BY_CABLE_POSITION.entrySet()) {
//            Level level = entry.getKey();
//            Long2ObjectMap<CableNetwork> networksByCablePosition = entry.getValue();
//            SFM.LOGGER.debug("Level {} has {} cables", level, networksByCablePosition.size());
//        }
    }

    public static Optional<CableNetwork> getOrRegisterNetworkFromManagerPosition(ManagerBlockEntity tile) {
        Level level = tile.getLevel();
        assert level != null;
        return getOrRegisterNetworkFromCablePosition(level, tile.getBlockPos());
    }

    public static Stream<CableNetwork> getNetworksForLevel(Level level) {
        if (level.isClientSide()) return Stream.empty();
        return NETWORKS_BY_LEVEL
                .getOrDefault(level, Collections.emptyList())
                .stream();
    }

    public static Stream<CableNetwork> getNetworksInRange(Level level, BlockPos pos, double maxDistance) {
        if (level.isClientSide()) return Stream.empty();
        return getNetworksForLevel(level)
                .filter(net -> net
                        .getCablePositions()
                        .anyMatch(cablePos -> cablePos.distSqr(pos) < maxDistance * maxDistance));
    }

    public static void unregisterNetworkForTestingPurposes(CableNetwork network) {
        removeNetwork(network);
    }

    public static void onCablePlaced(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        getOrRegisterNetworkFromCablePosition(level, pos);
    }

    public static void onCableRemoved(Level level, BlockPos cablePos) {
        getNetworkFromCablePosition(level, cablePos).ifPresent(network -> {
            // Invalidate the original network
            removeNetwork(network);
            // Only rebuild cache if fairly small network
            if (network.getCableCount() <= 256) {
                // Register networks that result from the removal of the cable, if any
                var remainingNetworks = network.withoutCable(cablePos);
                remainingNetworks.forEach(CableNetworkManager::addNetwork);
            }
        });
    }

    public static void purgeCableNetworkForManager(ManagerBlockEntity manager) {
        //noinspection DataFlowIssue
        getNetworkFromCablePosition(
                manager.getLevel(),
                manager.getBlockPos()
        ).ifPresent(CableNetworkManager::removeNetwork);
    }

    /**
     * Gets the cable network object. If none exists and one should, it will create and populate
     * one.
     * <p>
     * Networks should only exist on the server side.
     */
    public static Optional<CableNetwork> getOrRegisterNetworkFromCablePosition(Level level, BlockPos pos) {
        if (level.isClientSide()) return Optional.empty();

        // discover existing network for this position
        Optional<CableNetwork> existing = getNetworkFromCablePosition(level, pos);
        if (existing.isPresent()) return existing;

        // no existing network at this location, will either create one or merge into an existing one

        // only cables define the main spine of a network
        if (!CableNetwork.isCable(level, pos)) return Optional.empty();

        // find potential networks by getting networks adjacent to this cable
        ArrayDeque<BlockPos> danglingCables = new ArrayDeque<>(6);
        Set<CableNetwork> neighbouringNetworks = new HashSet<>();

        {
            BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
            for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                target.set(pos).move(direction);
                Optional<CableNetwork> found = getNetworkFromCablePosition(level, target);
                if (found.isPresent()) {
                    neighbouringNetworks.add(found.get());
                } else if (CableNetwork.isCable(level, target)) {
                    danglingCables.add(target.immutable());
                }
            }
        }

        // no candidates, create new network and end early
        if (neighbouringNetworks.isEmpty()) {
            CableNetwork network = new CableNetwork(level);
            // rebuild network from world
            // might be first time used after loading from disk
            network.rebuildNetwork(pos);
            addNetwork(network);
            return Optional.of(network);
        }

        // candidates exist, the new cable will result in a single merged network

        List<CableNetwork> networksByLevel = NETWORKS_BY_LEVEL.get(level);
        Long2ObjectMap<CableNetwork> networksByPosition = NETWORKS_BY_CABLE_POSITION.get(level);
        CableNetwork rtn;
        if (neighbouringNetworks.size() == 1) {
            // exactly one candidate exists
            rtn = neighbouringNetworks.iterator().next();
        } else {
            // More than one candidate network exists, merge them all into the first
            Iterator<CableNetwork> iterator = neighbouringNetworks.iterator();
            rtn = iterator.next();
            while (iterator.hasNext()) {
                CableNetwork other = iterator.next();
                rtn.mergeNetwork(other);
                networksByLevel.remove(other);
                other.getCablePositionsRaw().forEach(cablePos -> networksByPosition.put(cablePos, rtn));
            }
        }

        // add the new cable to the result network
        rtn.addCable(pos);
        networksByPosition.put(pos.asLong(), rtn);

        // add any dangling cables to the result network
        Set<BlockPos> visitDebounce = new HashSet<>();
        Set<BlockPos> allDanglingCables = SFMStreamUtils.<BlockPos, BlockPos>getRecursiveStream(
                (current, next, results) -> {
                    results.accept(current);
                    BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
                    for (Direction d : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                        target.set(current).move(d);
                        if (CableNetwork.isCable(rtn.getLevel(), target) && !rtn.containsCablePosition(target)) {
                            next.accept(target.immutable());
                        }
                    }
                },
                visitDebounce,
                danglingCables
        ).collect(Collectors.toSet());
        for (BlockPos danglingCable : allDanglingCables) {
            rtn.addCable(danglingCable);
            networksByPosition.put(danglingCable.asLong(), rtn);
        }

        onNetworkLookupChanged();
        return Optional.of(rtn);
    }

    public static List<BlockPos> getBadCableCachePositions(Level level) {
        return getNetworksForLevel(level)
                .flatMap(CableNetwork::getCablePositions)
                .filter(pos -> !(level.getBlockState(pos).getBlock() instanceof ICableBlock))
                .collect(Collectors.toList());
    }

    public static void clear() {
        NETWORKS_BY_LEVEL.clear();
        NETWORKS_BY_CABLE_POSITION.clear();
        onNetworkLookupChanged();
    }

    private static Optional<CableNetwork> getNetworkFromCablePosition(Level level, BlockPos pos) {
        return Optional.ofNullable(NETWORKS_BY_CABLE_POSITION
                                           .computeIfAbsent(level, k -> new Long2ObjectOpenHashMap<>())
                                           .get(pos.asLong()));
    }

    private static void removeNetwork(CableNetwork network) {
        // Unregister network from level lookup
        NETWORKS_BY_LEVEL.getOrDefault(network.getLevel(), Collections.emptyList()).remove(network);

        // Unregister network from cable position lookup
        Long2ObjectMap<CableNetwork> posMap = NETWORKS_BY_CABLE_POSITION
                .computeIfAbsent(network.getLevel(), k -> new Long2ObjectOpenHashMap<>());
        network.getCablePositionsRaw().forEach(posMap::remove);
        onNetworkLookupChanged();
    }

    private static void addNetwork(CableNetwork network) {
        // Register network to level lookup
        NETWORKS_BY_LEVEL.computeIfAbsent(network.getLevel(), k -> new ArrayList<>()).add(network);

        // Register network to cable position lookup
        Long2ObjectMap<CableNetwork> posMap = NETWORKS_BY_CABLE_POSITION
                .computeIfAbsent(network.getLevel(), k -> new Long2ObjectOpenHashMap<>());
        network.getCablePositionsRaw().forEach(cablePos -> posMap.put(cablePos, network));
        onNetworkLookupChanged();
    }


    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var chunk = event.getChunk();
        purgeChunkFromCableNetworks(level, chunk);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        NETWORKS_BY_LEVEL.remove(level);
        NETWORKS_BY_CABLE_POSITION.remove(level);
    }

    public static void purgeChunkFromCableNetworks(ServerLevel level, ChunkAccess chunkAccess) {
        getNetworksForLevel(level).forEach(network -> network.bustCacheForChunk(chunkAccess));
    }
}
