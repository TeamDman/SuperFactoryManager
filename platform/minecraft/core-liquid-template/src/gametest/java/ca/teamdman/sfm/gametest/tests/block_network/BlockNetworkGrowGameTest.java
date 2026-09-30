package ca.teamdman.sfm.gametest.tests.block_network;

import ca.teamdman.sfm.common.block_network.BlockNetwork;
import ca.teamdman.sfm.common.block_network.BlockNetworkConstructor;
import ca.teamdman.sfm.common.block_network.BlockNetworkManager;
import ca.teamdman.sfm.common.block_network.BlockNetworkMemberFilterMapper;
import ca.teamdman.sfm.common.util.SFMBlockPosUtils;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;

import java.util.Objects;

@SFMGameTest
public class BlockNetworkGrowGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        SFMGameTestLevel<String> testLevel = new SFMGameTestLevel<>("overworld");

        BlockNetworkMemberFilterMapper<SFMGameTestLevel<String>, String> memberFilterMapper = (level, pos) -> {
            String blockString = level.blocks().getFromPosition(pos);
            if (blockString == null) {
                return null;
            } else {
                return "member entity: " + blockString;
            }
        };
        BlockNetworkConstructor<SFMGameTestLevel<String>, String, BlockNetwork<SFMGameTestLevel<String>, String>> networkConstructor =
                BlockNetwork::new;
        BlockNetworkManager<SFMGameTestLevel<String>, String, BlockNetwork<SFMGameTestLevel<String>, String>> blockNetworkManager = new BlockNetworkManager<>(
                memberFilterMapper,
                networkConstructor
        );

        helper.assertTrue(blockNetworkManager.isEmpty(), "Default block network manager must be empty");

        BlockPos cablePos1 = new BlockPos(0, 0, 0);
        assertNull(helper, testLevel.getBlock(cablePos1), "First cable position should start empty");
        testLevel.setBlock(cablePos1, "block 1");
        assertNotNull(helper, testLevel.getBlock(cablePos1), "First cable position should contain a block");

        BlockNetwork<SFMGameTestLevel<String>, String> network1 = blockNetworkManager.getOrRegisterNetworkFromMemberPosition(
                testLevel,
                cablePos1
        );
        assertNotNull(helper, network1, "First cable network should be created");
        helper.assertTrue(!network1.isEmpty(), "First cable network should not be empty");
        assertEquals(helper, 1, network1.size(), "First cable network size");
        helper.assertTrue(network1.containsBlockPos(cablePos1), "First cable network should contain the first cable");
        helper.assertTrue(blockNetworkManager.containsLevel(testLevel), "Manager should contain the test level");

        BlockPos cablePos2 = new BlockPos(1, 0, 0);
        assertNull(helper, testLevel.getBlock(cablePos2), "Second cable position should start empty");
        helper.assertTrue(SFMBlockPosUtils.isAdjacent(cablePos1, cablePos2), "First and second cable should be adjacent");
        testLevel.setBlock(cablePos2, "block 2");
        assertNotNull(helper, testLevel.getBlock(cablePos2), "Second cable position should contain a block");

        BlockNetwork<SFMGameTestLevel<String>, String> network2 = blockNetworkManager.onMemberAddedToLevel(testLevel, cablePos2);
        assertNotNull(helper, network2, "Second cable network should be present");
        helper.assertTrue(!network2.isEmpty(), "Second cable network should not be empty");
        helper.assertTrue(network2.containsBlockPos(cablePos1), "Second cable network should contain the first cable");
        helper.assertTrue(network2.containsBlockPos(cablePos2), "Second cable network should contain the second cable");
        helper.assertTrue(blockNetworkManager.containsLevel(testLevel), "Manager should still contain the test level");
        assertEquals(helper, network1, network2, "Second cable should use the first network");
        assertEquals(helper, 2, network2.size(), "Second cable network size");
        assertEquals(helper, System.identityHashCode(network1), System.identityHashCode(network2), "Network identity hash");
        assertEquals(helper, 1, blockNetworkManager.networkCount(), "Network count after second cable");

        BlockPos cablePos3 = new BlockPos(-1, 0, 0);
        assertNull(helper, testLevel.getBlock(cablePos3), "Third cable position should start empty");
        helper.assertTrue(SFMBlockPosUtils.isAdjacent(cablePos1, cablePos3), "First and third cable should be adjacent");
        testLevel.setBlock(cablePos3, "block 3");
        assertNotNull(helper, testLevel.getBlock(cablePos3), "Third cable position should contain a block");

        BlockNetwork<SFMGameTestLevel<String>, String> network3 = blockNetworkManager.onMemberAddedToLevel(testLevel, cablePos3);
        assertNotNull(helper, network3, "Third cable network should be present");
        helper.assertTrue(!network3.isEmpty(), "Third cable network should not be empty");
        helper.assertTrue(network3.containsBlockPos(cablePos1), "Third cable network should contain the first cable");
        helper.assertTrue(network3.containsBlockPos(cablePos2), "Third cable network should contain the second cable");
        helper.assertTrue(network3.containsBlockPos(cablePos3), "Third cable network should contain the third cable");
        helper.assertTrue(blockNetworkManager.containsLevel(testLevel), "Manager should still contain the test level");
        assertEquals(helper, network2, network3, "Third cable should use the existing network");
        assertEquals(helper, 3, network3.size(), "Third cable network size");

        testLevel.setBlock(cablePos3, null);
        blockNetworkManager.onMemberRemovedFromLevel(testLevel, cablePos3);

        testLevel.setBlock(cablePos3, "block 3");
        blockNetworkManager.onMemberAddedToLevel(testLevel, cablePos3);

        testLevel.setBlock(cablePos1, null);
        blockNetworkManager.onMemberRemovedFromLevel(testLevel, cablePos1);
        assertEquals(helper, 2, blockNetworkManager.networkCount(), "Network count after splitting");

        helper.succeed();
    }

    private static void assertNull(
            SFMGameTestHelper helper,
            Object value,
            String message
    ) {
        helper.assertTrue(value == null, message + ": expected null but got " + value);
    }

    private static void assertNotNull(
            SFMGameTestHelper helper,
            Object value,
            String message
    ) {
        helper.assertTrue(value != null, message + ": expected non-null value");
    }

    private static void assertEquals(
            SFMGameTestHelper helper,
            Object expected,
            Object actual,
            String message
    ) {
        helper.assertTrue(
                Objects.equals(expected, actual),
                message + ": expected " + expected + " but got " + actual
        );
    }
}
