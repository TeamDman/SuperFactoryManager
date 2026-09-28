package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * Compares a Mekanism energy output through a tunnel with a directly adjacent
 * output while the destination block entity disappears and returns across ticks.
 * This is a provider-replacement probe, not a substitute for a full world relog.
 */
@SFMGameTest
public class TunnelledMekanismEnergyRecoveryGameTest extends SFMGameTestDefinition {
    private static final BlockPos TUNNEL_SOURCE = new BlockPos(0, 2, 1);
    private static final BlockPos TUNNEL = new BlockPos(1, 2, 1);
    private static final BlockPos TUNNEL_DESTINATION = new BlockPos(2, 2, 1);
    private static final BlockPos DIRECT_SOURCE = new BlockPos(0, 2, 3);
    private static final BlockPos DIRECT_DESTINATION = new BlockPos(1, 2, 3);

    @Override
    public String template() {
        return "3x3x5";
    }

    @Override
    public String testName() {
        return "tunnelled_mekanism_energy_recovery_after_provider_replacement";
    }

    @Override
    public int maxTicks() {
        return 100;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(TUNNEL, SFMBlocks.TUNNELLED_CABLE.get());
        placeCube(helper, TUNNEL_SOURCE, Direction.EAST, DataType.OUTPUT);
        placeCube(helper, TUNNEL_DESTINATION, Direction.WEST, DataType.INPUT);
        placeCube(helper, DIRECT_SOURCE, Direction.EAST, DataType.OUTPUT);
        placeCube(helper, DIRECT_DESTINATION, Direction.WEST, DataType.INPUT);
        charge(helper, TUNNEL_SOURCE);
        charge(helper, DIRECT_SOURCE);

        helper.runAfterDelay(20, () -> {
            assertReceived(helper, TUNNEL_DESTINATION, "tunnel baseline");
            assertReceived(helper, DIRECT_DESTINATION, "direct baseline");
            helper.setBlock(TUNNEL_DESTINATION, Blocks.AIR);
            helper.setBlock(DIRECT_DESTINATION, Blocks.AIR);
            charge(helper, TUNNEL_SOURCE);
            charge(helper, DIRECT_SOURCE);
        });

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(helper.getBlockEntity(TUNNEL_DESTINATION) == null,
                    "Tunnel destination must have been absent across server ticks");
            helper.assertTrue(helper.getBlockEntity(DIRECT_DESTINATION) == null,
                    "Direct destination must have been absent across server ticks");
            placeCube(helper, TUNNEL_DESTINATION, Direction.WEST, DataType.INPUT);
            placeCube(helper, DIRECT_DESTINATION, Direction.WEST, DataType.INPUT);
        });

        helper.runAfterDelay(80, () -> {
            assertReceived(helper, DIRECT_DESTINATION, "direct recovery control");
            assertReceived(helper, TUNNEL_DESTINATION, "tunnel recovery without replacement");
            helper.succeed();
        });
    }

    private static TileEntityEnergyCube placeCube(
            SFMGameTestHelper helper,
            BlockPos pos,
            Direction side,
            DataType dataType
    ) {
        helper.setBlock(pos, MekanismBlocks.BASIC_ENERGY_CUBE.getBlock());
        TileEntityEnergyCube cube = helper.getBlockEntity(pos, TileEntityEnergyCube.class);
        RelativeSide relativeSide = null;
        for (RelativeSide candidate : RelativeSide.values()) {
            if (candidate.getDirection(cube.getDirection()) == side) {
                relativeSide = candidate;
                break;
            }
        }
        helper.assertTrue(relativeSide != null, "No Mekanism side maps to " + side);
        SFMMekanismCompat.configureExclusiveIO(cube, TransmissionType.ENERGY, relativeSide, dataType);
        cube.getConfig().getConfig(TransmissionType.ENERGY).setEjecting(dataType == DataType.OUTPUT);
        return cube;
    }

    private static void charge(SFMGameTestHelper helper, BlockPos pos) {
        helper.getBlockEntity(pos, TileEntityEnergyCube.class)
                .setEnergy(0, SFMMekanismCompat.createForgeEnergy(1000));
    }

    private static void assertReceived(SFMGameTestHelper helper, BlockPos pos, String phase) {
        TileEntityEnergyCube cube = helper.getBlockEntity(pos, TileEntityEnergyCube.class);
        helper.assertTrue(!cube.getEnergy(0).isZero(), phase + " received no energy");
    }
}
