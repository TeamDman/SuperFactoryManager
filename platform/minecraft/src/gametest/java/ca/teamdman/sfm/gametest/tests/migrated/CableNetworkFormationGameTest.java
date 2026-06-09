package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;



/**
 * Migrated from SFMCorrectnessGameTests.cable_network_formation
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class CableNetworkFormationGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "25x4x25";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // create a row of cables
        for (int i = 0; i < 10; i++) {
            helper.setBlock(new BlockPos(i, 1, 0), SFMBlocks.CABLE.get());
        }

        var net = CableNetworkManager
                .getOrRegisterNetworkFromCablePosition(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)))
                .get();
        // those cables should all be on the same network
        for (int i = 0; i < 10; i++) {
            helper.assertTrue(
                    CableNetworkManager
                               .getOrRegisterNetworkFromCablePosition(
                                       helper.getLevel(),
                                       helper.absolutePos(new BlockPos(i, 1, 0))
                               )
                               .get() == net, "Line of ten should be on same network",
                    helper.getTick());
        }

        // the network should only contain those cables
        helper.assertTrue(net.getCableCount() == 10, "Network size should be ten", helper.getTick());

        // break a block in the middle of the cable
        helper.setBlock(new BlockPos(5, 1, 0), Blocks.AIR);
        // the network should split
        net = CableNetworkManager
                .getOrRegisterNetworkFromCablePosition(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)))
                .get();
        // now we have a network of 5 cables and a network of 4 cables
        for (int i = 0; i < 5; i++) {
            helper.assertTrue(
                    CableNetworkManager
                               .getOrRegisterNetworkFromCablePosition(
                                       helper.getLevel(),
                                       helper.absolutePos(new BlockPos(i, 1, 0))
                               )
                               .get() == net, "Row of five should be same network after splitting",
                    helper.getTick());
        }
        var old = net;
        net = CableNetworkManager
                .getOrRegisterNetworkFromCablePosition(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 0)))
                .get();
        helper.assertTrue(old != net, "Networks should be distinct after splitting", helper.getTick());
        for (int i = 6; i < 10; i++) {
            helper.assertTrue(
                    CableNetworkManager
                               .getOrRegisterNetworkFromCablePosition(
                                       helper.getLevel(),
                                       helper.absolutePos(new BlockPos(i, 1, 0))
                               )
                               .get() == net, "Remaining row should be same network after splitting",
                    helper.getTick());
        }

        // repair the cable
        helper.setBlock(new BlockPos(5, 1, 0), SFMBlocks.CABLE.get());
        // the network should merge
        net = CableNetworkManager
                .getOrRegisterNetworkFromCablePosition(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)))
                .get();
        for (int i = 0; i < 10; i++) {
            helper.assertTrue(
                    CableNetworkManager
                               .getOrRegisterNetworkFromCablePosition(
                                       helper.getLevel(),
                                       helper.absolutePos(new BlockPos(i, 1, 0))
                               )
                               .get() == net, "Networks should merge to same network after repairing",
                    helper.getTick());
        }

        // add cables in the corner
        helper.setBlock(new BlockPos(0, 1, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(1, 1, 1), SFMBlocks.CABLE.get());
        helper.assertTrue(
                CableNetworkManager
                           .getOrRegisterNetworkFromCablePosition(
                                   helper.getLevel(),
                                   helper.absolutePos(new BlockPos(0, 1, 0))
                           )
                           .get()
                           .getCableCount() == 12, "Network should grow to twelve after adding two cables",
                helper.getTick());

        // punch out the corner, the network should shrink by 1
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        helper.assertTrue(
                CableNetworkManager
                           .getOrRegisterNetworkFromCablePosition(
                                   helper.getLevel(),
                                   helper.absolutePos(new BlockPos(0, 1, 0))
                           )
                           .get()
                           .getCableCount() == 11, "Network should shrink to eleven after removing a cable",
                helper.getTick());


        // create a new network in a plus shape
        helper.setBlock(new BlockPos(15, 1, 15), SFMBlocks.CABLE.get());
        for (Direction value : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            helper.setBlock(new BlockPos(15, 1, 15).relative(value), SFMBlocks.CABLE.get());
        }
        // should all be on the same network
        net = CableNetworkManager
                .getOrRegisterNetworkFromCablePosition(helper.getLevel(), helper.absolutePos(new BlockPos(15, 1, 15)))
                .get();
        for (Direction value : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            helper.assertTrue(
                    CableNetworkManager
                               .getOrRegisterNetworkFromCablePosition(
                                       helper.getLevel(),
                                       helper.absolutePos(new BlockPos(15, 1, 15).relative(value))
                               )
                               .get()
                       == net, "Plus cables should all be on the same network",
                    helper.getTick());
        }

        // break the block in the middle
        helper.setBlock(new BlockPos(15, 1, 15), Blocks.AIR);
        // the network should split
        helper.assertTrue(
                CableNetworkManager
                           .getOrRegisterNetworkFromCablePosition(
                                   helper.getLevel(),
                                   helper.absolutePos(new BlockPos(15, 1, 15))
                           )
                           .isEmpty(), "Network should not be present where the cable was removed from",
                helper.getTick());
        var networks = new ArrayList<CableNetwork>();
        for (Direction value : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            networks.add(CableNetworkManager
                                 .getOrRegisterNetworkFromCablePosition(
                                         helper.getLevel(),
                                         helper.absolutePos(new BlockPos(15, 1, 15).relative(value))
                                 )
                                 .get());
        }
        // make sure all the networks are different
        for (CableNetwork network : networks) {
            helper.assertTrue(
                    networks.stream().filter(n -> n == network).count() == 1,
                    "Broken plus networks should be distinct",
                    helper.getTick()
            );
        }

        // add the block back
        helper.setBlock(new BlockPos(15, 1, 15), SFMBlocks.CABLE.get());
        // the network should merge
        net = CableNetworkManager
                .getOrRegisterNetworkFromCablePosition(helper.getLevel(), helper.absolutePos(new BlockPos(15, 1, 15)))
                .get();
        for (Direction value : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            helper.assertTrue(
                    CableNetworkManager
                               .getOrRegisterNetworkFromCablePosition(
                                       helper.getLevel(),
                                       helper.absolutePos(new BlockPos(15, 1, 15).relative(value))
                               )
                               .get()
                       == net, "Plus networks did not merge after repairing",
                    helper.getTick());
        }

        // let's also test having cables in more than just a straight line
        // we want corners with multiple cables adjacent

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                helper.setBlock(new BlockPos(7 + i, 1, 7 + j), SFMBlocks.CABLE.get());
            }
        }
        // make sure it's all in a single network
        helper.assertTrue(
                CableNetworkManager
                           .getOrRegisterNetworkFromCablePosition(
                                   helper.getLevel(),
                                   helper.absolutePos(new BlockPos(7, 1, 7))
                           )
                           .get()
                           .getCableCount() == 25, "Network cable count should be 25",
                helper.getTick());
        // cut a line through it
        for (int i = 0; i < 5; i++) {
            helper.setBlock(new BlockPos(7 + i, 1, 9), Blocks.AIR);
        }

        // make sure the network disappeared where it was cut
        helper.assertTrue(
                CableNetworkManager
                           .getOrRegisterNetworkFromCablePosition(
                                   helper.getLevel(),
                                   helper.absolutePos(new BlockPos(7, 1, 9))
                           )
                           .isEmpty(), "Network should not be present where the cable was removed from",
                helper.getTick());
        // make sure new network of 10 is formed
        helper.assertTrue(
                CableNetworkManager
                           .getOrRegisterNetworkFromCablePosition(
                                   helper.getLevel(),
                                   helper.absolutePos(new BlockPos(7, 1, 8))
                           )
                           .get()
                           .getCableCount() == 10, "New network should be size ten",
                helper.getTick());
        // make sure new network of 10 is formed
        helper.assertTrue(
                CableNetworkManager
                           .getOrRegisterNetworkFromCablePosition(
                                   helper.getLevel(),
                                   helper.absolutePos(new BlockPos(7, 1, 11))
                           )
                           .get()
                           .getCableCount() == 10, "Other new network should be size ten",
                helper.getTick());
        // make sure the new networks are distinct
        helper.assertTrue(
                CableNetworkManager
                           .getOrRegisterNetworkFromCablePosition(
                                   helper.getLevel(),
                                   helper.absolutePos(new BlockPos(7, 1, 8))
                           )
                           .get() != CableNetworkManager
                           .getOrRegisterNetworkFromCablePosition(
                                   helper.getLevel(),
                                   helper.absolutePos(new BlockPos(7, 1, 11))
                           )
                           .get(), "New networks should be distinct",
                helper.getTick());


        helper.succeed();
    }
}
