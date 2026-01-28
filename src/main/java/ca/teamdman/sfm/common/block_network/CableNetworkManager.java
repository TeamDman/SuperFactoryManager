package ca.teamdman.sfm.common.block_network;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;

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
    private static final BlockNetworkManager<Level, Unit, CableNetwork> NETWORK_MANAGER = new BlockNetworkManager<>(
            CableNetwork::cableMemberFilterMapper,
            CableNetwork::new
    );

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
                        .anyMatch(cablePos -> cablePos.distSqr(pos) < maxDistance * maxDistance));
    }

    public static void unregisterNetworkForTestingPurposes(CableNetwork network) {
        NETWORK_MANAGER.untrackNetwork(network);
    }

    public static void onCablePlaced(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        NETWORK_MANAGER.onMemberAddedToLevel(level, pos);
    }

    public static void onCableRemoved(Level level, BlockPos cablePos) {
        if (level.isClientSide()) return;
        NETWORK_MANAGER.onMemberRemovedFromLevel(level, cablePos);
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

    public static List<BlockPos> getBadCableCachePositions(Level level) {

        return NETWORK_MANAGER.getNetworksForLevel(level)
                .values()
                .stream()
                .flatMap(CableNetwork::getCablePositions)
                .filter(pos -> !(level.getBlockState(pos).getBlock() instanceof ICableBlock))
                .collect(Collectors.toList());
    }

    public static void clear() {
        NETWORK_MANAGER.clear();
    }

    @SFMSubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var chunk = event.getChunk();
        NETWORK_MANAGER.clearChunk(level, chunk.getPos());
    }

    @SFMSubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        NETWORK_MANAGER.clearLevel(level);
    }
}
