package ca.teamdman.sfm.common.block_network;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import java.util.List;
import java.util.Optional;
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
    private static final BlockNetworkManager<World, CableType, CableNetwork> NETWORK_MANAGER = new BlockNetworkManager<>(
            CableNetwork::cableMemberFilterMapper,
            CableNetwork::new
    );

    public static Optional<CableNetwork> getOrRegisterNetworkFromManagerPosition(ManagerBlockEntity tile) {
        World level = tile.getLevel();
        assert level != null;
        return getOrRegisterNetworkFromCablePosition(level, tile.getBlockPos());
    }

    public static Stream<CableNetwork> getNetworksInRange(World level, BlockPos pos, double maxDistance) {
        if (level.isRemote) return Stream.empty();
        return NETWORK_MANAGER.getNetworksForLevel(level).values().stream()
                // .distinct()
                .filter(net -> net
                        .getCablePositions()
                        .stream()
                        .anyMatch(cablePos -> cablePos.distanceSq(pos) < maxDistance * maxDistance));
    }

    public static void unregisterNetworkForTestingPurposes(CableNetwork network) {
        NETWORK_MANAGER.untrackNetwork(network);
    }

    public static void onCablePlaced(World level, BlockPos pos) {
        if (level.isRemote) return;
        NETWORK_MANAGER.onMemberAddedToLevel(level, pos);
        var network = NETWORK_MANAGER.getNetwork(level, pos);
        if (network != null) {
            network.updateVisualManagers();
        }
    }

    public static void onCableRemoved(World level, BlockPos cablePos) {
        if (level.isRemote) return;
        var network = NETWORK_MANAGER.getNetwork(level, cablePos);
        NETWORK_MANAGER.onMemberRemovedFromLevel(level, cablePos);
        if (network != null) {
            network.updateVisualManagers(cablePos);
        }
    }

    public static void purgeCableNetworkForManager(ManagerBlockEntity manager) {
        World level = manager.getLevel();
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
    public static Optional<CableNetwork> getOrRegisterNetworkFromCablePosition(World level, BlockPos pos) {
        return getOrRegisterNetworkFromCablePosition(level, pos, false);
    }
    public static Optional<CableNetwork> getOrRegisterNetworkFromCablePosition(World level, BlockPos pos, boolean isVisualManager) {
        if (level.isRemote) return Optional.empty();

        CableNetwork network = NETWORK_MANAGER.onMemberAddedToLevel(level, pos);
        return Optional.ofNullable(network);
    }

    public static List<BlockPos> getBadCableCachePositions(World level) {

        return NETWORK_MANAGER.getNetworksForLevel(level)
                .values()
                .stream()
                .flatMap(network -> network.getCablePositions().stream())
                .filter(pos -> !(level.getBlockState(pos).getBlock() instanceof ICableBlock))
                .collect(Collectors.toList());
    }

    public static void clear() {
        NETWORK_MANAGER.clear();
    }

    @SFMSubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getWorld().isRemote) return;
        if (!(event.getWorld() instanceof WorldServer level)) return;
        var chunk = event.getChunk();
        NETWORK_MANAGER.clearChunk(level, chunk.getPos());
    }

    @SFMSubscribeEvent
    public static void onLevelUnload(WorldEvent.Unload event) {
        if (!(event.getWorld() instanceof WorldServer level)) return;
        NETWORK_MANAGER.clearLevel(level);
    }
}
