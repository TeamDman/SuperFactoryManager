package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import mekanism.common.tile.TileEntityEnergyCube;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Compares the persisted tunnel with a direct Mekanism transfer after a real world reload. */
public final class AssertMekanismTunnelEnergyAfterRejoinPuppetAction implements SFMPuppetAction {
    private static final BlockPos TUNNEL_SOURCE = new BlockPos(0, 2, 1);
    private static final BlockPos TUNNEL = new BlockPos(1, 2, 1);
    private static final BlockPos TUNNEL_DESTINATION = new BlockPos(2, 2, 1);
    private static final BlockPos DIRECT_SOURCE = new BlockPos(0, 2, 3);
    private static final BlockPos DIRECT_DESTINATION = new BlockPos(1, 2, 3);
    private static final int SETTLE_CLIENT_TICKS = 80;

    private int phase;
    private int waitTicks;
    private volatile boolean serverTaskDone;
    private volatile Throwable serverTaskFailure;

    @Override
    public String description() {
        return "assert Mekanism direct and tunnelled energy delivery after relog";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (serverTaskFailure != null) {
            throw new IllegalStateException("Mekanism relog probe failed", serverTaskFailure);
        }
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || !server.isReady()) {
            return false;
        }
        BlockPos tunnelSource = runtime.absoluteGameTestPos(TUNNEL_SOURCE);
        BlockPos tunnel = runtime.absoluteGameTestPos(TUNNEL);
        BlockPos tunnelDestination = runtime.absoluteGameTestPos(TUNNEL_DESTINATION);
        BlockPos directSource = runtime.absoluteGameTestPos(DIRECT_SOURCE);
        BlockPos directDestination = runtime.absoluteGameTestPos(DIRECT_DESTINATION);

        if (phase == 0) {
            phase = 1;
            server.execute(() -> runServerTask(() -> {
                ServerLevel level = server.overworld();
                if (level.getBlockState(tunnel).getBlock() != SFMBlocks.TUNNELLED_CABLE.get()) {
                    throw new IllegalStateException("The persisted tunnel block is missing after relog");
                }
                cube(level, tunnelDestination).setEnergy(0, SFMMekanismCompat.createForgeEnergy(0));
                cube(level, directDestination).setEnergy(0, SFMMekanismCompat.createForgeEnergy(0));
                cube(level, tunnelSource).setEnergy(0, SFMMekanismCompat.createForgeEnergy(1000));
                cube(level, directSource).setEnergy(0, SFMMekanismCompat.createForgeEnergy(1000));
            }));
            return false;
        }
        if (phase == 1) {
            if (!serverTaskDone) return false;
            serverTaskDone = false;
            phase = 2;
        }
        if (phase == 2) {
            if (++waitTicks < SETTLE_CLIENT_TICKS) return false;
            phase = 3;
            server.execute(() -> runServerTask(() -> {
                ServerLevel level = server.overworld();
                boolean directReceived = !cube(level, directDestination).getEnergy(0).isZero();
                boolean tunnelReceived = !cube(level, tunnelDestination).getEnergy(0).isZero();
                SFM.LOGGER.info("SFM_GAME_PUPPET_RELOG_ENERGY_PROBE direct_received={} tunnel_received={}",
                        directReceived, tunnelReceived);
                if (!directReceived) {
                    throw new IllegalStateException("Direct Mekanism control received no energy after relog");
                }
                if (!tunnelReceived) {
                    throw new IllegalStateException("Tunnelled Mekanism destination received no energy after relog");
                }
            }));
            return false;
        }
        return serverTaskDone;
    }

    private static TileEntityEnergyCube cube(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TileEntityEnergyCube cube) return cube;
        throw new IllegalStateException("Mekanism energy cube missing at " + pos);
    }

    private void runServerTask(Runnable task) {
        try {
            task.run();
        } catch (Throwable throwable) {
            serverTaskFailure = throwable;
        } finally {
            serverTaskDone = true;
        }
    }
}
