package ca.teamdman.sfm.common.block_network;

import ca.teamdman.sfm.common.blockentity.WaterTankBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WaterNetworkManager {
    private static final BlockNetworkManager<Level, WaterTankBlockEntity> NETWORK_MANAGER = new BlockNetworkManager<>(
            (level, blockPos) -> {
                BlockEntity blockEntity = level.getBlockEntity(blockPos);
                if (blockEntity instanceof WaterTankBlockEntity waterTankBlockEntity) {
                    return waterTankBlockEntity;
                } else {
                    return null;
                }
            }
    );

    public static void onLoad(WaterTankBlockEntity blockEntity) {

        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide()) return;
        onWaterTankBlockActiveStateChanged(level, blockEntity.getBlockPos());
    }

    public static void onWaterTankBlockActiveStateChanged(
            Level level,
            BlockPos blockPos
    ) {

        // Only proceed on the server
        if (level.isClientSide()) return;

        // Update the water tank activeness
        if (level.getBlockEntity(blockPos) instanceof WaterTankBlockEntity waterTankBlockEntity) {
            waterTankBlockEntity.updateActiveFromBlockState();
        }

        // Update water tank capacities on the network
        updateWaterTankCapacitiesForNetworkOfMember(level, blockPos);

        if (SFMEnvironmentUtils.isInIDE()) {
            printNetworks();
        }
    }

    public static void onWaterTankBlockRemoved(
            Level level,
            BlockPos blockPos
    ) {

        // Only proceed on the server
        if (level.isClientSide()) return;

        // Remove the member
        List<BlockNetwork<Level, WaterTankBlockEntity>> newNetworks = NETWORK_MANAGER.onMemberRemovedFromLevel(
                level,
                blockPos
        );

        // Update water tank capacities of the networks that remain after removal
        for (BlockNetwork<Level, WaterTankBlockEntity> network : newNetworks) {
            updateWaterTankCapacitiesForNetwork(network);
        }


        if (SFMEnvironmentUtils.isInIDE()) {
            printNetworks();
        }
    }

    public static void printNetworks() {

        boolean enabled = false;
        if (!enabled) return;

        NETWORK_MANAGER.printDebugInfo();
    }

    /// Get the network for the given block position and update the capacities
    /// of the water tanks in that network.
    public static void updateWaterTankCapacitiesForNetworkOfMember(
            Level level,
            BlockPos blockPos
    ) {

        // Get the network
        @Nullable BlockNetwork<Level, WaterTankBlockEntity> network = NETWORK_MANAGER.onMemberAddedToLevel(
                level,
                blockPos
        );
        if (network == null) return;

        updateWaterTankCapacitiesForNetwork(network);

    }

    private static void updateWaterTankCapacitiesForNetwork(BlockNetwork<Level, WaterTankBlockEntity> network) {
        // Determine how many members of the network are active
        int activeMemberCount = 0;
        for (WaterTankBlockEntity member : network.members().values()) {
            if (member.isActive()) {
                activeMemberCount += 1;
            }
        }

        // Update the network member water tank capacities
        for (WaterTankBlockEntity member : network.members().values()) {
            member.updateTankCapacity(activeMemberCount);
        }
    }

    @SFMSubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {

        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        NETWORK_MANAGER.clearChunk(level, event.getChunk().getPos());
    }

    @SFMSubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {

        if (!(event.getLevel() instanceof ServerLevel level)) return;
        NETWORK_MANAGER.clearLevel(level);
    }

    public static void clear() {

        NETWORK_MANAGER.clear();

    }

}
