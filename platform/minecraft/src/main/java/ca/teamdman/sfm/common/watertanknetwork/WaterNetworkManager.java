package ca.teamdman.sfm.common.watertanknetwork;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.blockentity.WaterTankBlockEntity;
import ca.teamdman.sfm.common.util.SFMDirections;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = SFM.MOD_ID)
public class WaterNetworkManager {
    private static final Map<Level, Long2ObjectMap<WaterNetwork>> NETWORKS = new Object2ObjectOpenHashMap<>();

    public static Long2ObjectMap<WaterNetwork> getNetworksForLevel(Level level) {
        return NETWORKS.computeIfAbsent(level, k -> new Long2ObjectOpenHashMap<>());
    }

    public static void onLoad(WaterTankBlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide()) return;
        onActiveStateChanged(level, blockEntity.getBlockPos(), blockEntity.getBlockState());
    }

    private static void addMember(
            Level level,
            BlockPos pos
    ) {
        if (level.isClientSide()) return;
        getOrRegisterNetwork(level, pos).ifPresent(WaterNetwork::updateMembers);
    }

    private static void removeMember(
            Level level,
            BlockPos memberPos
    ) {
        if (level.isClientSide()) return;
        getNetwork(level, memberPos).ifPresent(network -> {
            // Disable the network member
            WaterTankBlockEntity member = network.getMember(memberPos);
            if (member != null) {
                member.setActive(false);
            }
            // Unregister the original network
            removeNetwork(network);
            // Register networks that result from the removal of the cable, if any
            for (WaterNetwork remainingNetwork : network.withoutMember(memberPos)) {
                addNetwork(remainingNetwork);
                remainingNetwork.updateMembers();
            }
        });
    }

    public static void onActiveStateChanged(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        if (state.getOptionalValue(WaterTankBlock.IN_WATER).orElse(false)) {
            addMember(level, pos);
        } else {
            removeMember(level, pos);
        }
//        if (SFMEnvironment.isInIDE()) {
//            Long2ObjectMap<WaterNetwork> levelNetworks = NETWORKS.get(level);
//            if (levelNetworks == null) return;
//            List<WaterNetwork> logNetworks = levelNetworks.values().stream().distinct().toList();
//            SFM.LOGGER.debug(
//                    "There are now {} networks ({})",
//                    logNetworks.size(),
//                    logNetworks
//                            .stream()
//                            .mapToInt(net -> net.members().size())
//                            .mapToObj(Integer::toString)
//                            .reduce((a, b) -> a + ", " + b)
//                            .orElse("")
//            );
//        }
    }

    /**
     * Gets the cable network object. If none exists and one should, it will create and populate
     * one.
     * <p>
     * Networks should only exist on the server side.
     * <p>
     *
     * The {@link ca.teamdman.sfm.common.cablenetwork.CableNetworkManager#getOrRegisterNetworkFromCablePosition(Level, BlockPos)} method has received some adjustments that this method has not adopted yet.
     * The water network feels like it's working, so I haven't bothered to update it yet ¯\_(ツ)_/¯
     */
    public static Optional<WaterNetwork> getOrRegisterNetwork(
            Level level,
            BlockPos pos
    ) {
        if (level.isClientSide()) return Optional.empty();

        // discover existing network for this position
        Optional<WaterNetwork> existing = getNetwork(level, pos);
        if (existing.isPresent()) return existing;

        // only active water tanks can be in a network
        if (!(level.getBlockEntity(pos) instanceof WaterTankBlockEntity blockEntity)) return Optional.empty();
        if (!level.getBlockState(pos).getValue(WaterTankBlock.IN_WATER)) return Optional.empty();

        // find potential networks
        Set<WaterNetwork> candidates = getAdjacentNetworks(level, pos);

        // no candidates, create new network
        if (candidates.isEmpty()) {
            WaterNetwork network = new WaterNetwork(level);
            // rebuild network from world
            // might be first time used after loading from disk
            network.rebuildNetwork(pos);
            addNetwork(network);
            return Optional.of(network);
        }

        // one candidate exists, add the member to it
        if (candidates.size() == 1) {
            // Only one network matches this member, add cable as member
            WaterNetwork network = candidates.iterator().next();
            network.addMember(pos);
            getNetworksForLevel(level).put(pos.asLong(), network);
            return Optional.of(network);
        }

        // more than one candidate network exists, merge them
        WaterNetwork result = mergeNetworks(candidates, blockEntity);
        return Optional.ofNullable(result);
    }

    public static void clear() {
        NETWORKS.clear();
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var chunk = event.getChunk();
        purgeChunk(level, chunk);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        NETWORKS.remove(level);
    }

    public static void purgeChunk(
            ServerLevel level,
            ChunkAccess chunkAccess
    ) {
        getNetworksForLevel(level).values().forEach(network -> network.purgeChunk(chunkAccess));
    }

    private static Optional<WaterNetwork> getNetwork(
            Level level,
            BlockPos pos
    ) {
        var network = getNetworksForLevel(level).get(pos.asLong());
        return Optional.ofNullable(network);
    }

    private static void removeNetwork(WaterNetwork network) {
        getNetworksForLevel(network.level()).keySet().removeAll(network.members().keySet());
    }

    private static void addNetwork(WaterNetwork network) {
        Long2ObjectMap<WaterNetwork> networksForLevel = getNetworksForLevel(network.level());
        network.members().keySet().forEach(pos -> networksForLevel.put(pos, network));
    }

    /**
     * Finds the set of networks that contain the given position
     */
    private static Set<WaterNetwork> getAdjacentNetworks(
            Level level,
            BlockPos pos
    ) {
        Set<WaterNetwork> rtn = new HashSet<>();
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            target.set(pos).move(direction);
            Optional<WaterNetwork> network = getNetwork(level, target);
            network.ifPresent(rtn::add);
        }
        return rtn;
    }

    private static @Nullable WaterNetwork mergeNetworks(Set<WaterNetwork> networks,
                                                        WaterTankBlockEntity blockEntity
    ) {
        if (networks.isEmpty()) return null;

        Iterator<WaterNetwork> iterator = networks.iterator();
        // The first network will absorb the others
        WaterNetwork main = iterator.next();
        // Merge the rest into the first
        iterator.forEachRemaining(main::mergeNetwork);
        // Add the merging block
        main.addMember(blockEntity);
        // the main network now contains all the cable positions of the others
        // when we addNetwork here, it _should_ clobber all the old entries to point to this network instead
        addNetwork(main);
        return main;
    }
}
