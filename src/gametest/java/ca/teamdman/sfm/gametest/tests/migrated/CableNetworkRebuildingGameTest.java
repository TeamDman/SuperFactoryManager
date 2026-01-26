package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.cable_network_rebuilding
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class CableNetworkRebuildingGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 2, 0), SFMBlocks.CABLE_BLOCK.get());
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.CABLE_BLOCK.get());
        helper.setBlock(new BlockPos(2, 2, 0), SFMBlocks.CABLE_BLOCK.get());
        var network = CableNetworkManager.getOrRegisterNetworkFromCablePosition(
                helper.getLevel(),
                helper.absolutePos(new BlockPos(0, 2, 0))
        );
        assertTrue(network.isPresent(), "Network should be built");
        CableNetworkManager.unregisterNetworkForTestingPurposes(network.get());
        network = CableNetworkManager.getOrRegisterNetworkFromCablePosition(
                helper.getLevel(),
                helper.absolutePos(new BlockPos(0, 2, 0))
        );
        assertTrue(network.isPresent(), "Network should be rebuilt after clearing");
        assertTrue(network.get().getCableCount() == 3, "Network rebuilding should discover 3 cables");
        helper.succeed();
    }
}
