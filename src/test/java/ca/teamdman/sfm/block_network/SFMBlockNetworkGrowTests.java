package ca.teamdman.sfm.block_network;

import ca.teamdman.sfm.common.block_network.BlockNetwork;
import ca.teamdman.sfm.common.block_network.BlockNetworkConstructor;
import ca.teamdman.sfm.common.block_network.BlockNetworkManager;
import ca.teamdman.sfm.common.block_network.BlockNetworkMemberFilterMapper;
import ca.teamdman.sfm.common.util.SFMBlockPosUtils;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SFMBlockNetworkGrowTests {

    @Test
    public void testNetworkGrow() {
        // Create a fake level stand-in
        SFMTestLevel<String> testLevel = new SFMTestLevel<>("overworld");

        // Create a block network manager
        BlockNetworkMemberFilterMapper<SFMTestLevel<String>, String> memberFilterMapper = (level, pos) -> {
            String blockString = level.blocks().get(pos);
            if (blockString == null) {
                return null;
            } else {
                return "member entity: " + blockString;
            }
        };
        BlockNetworkConstructor<SFMTestLevel<String>, String, BlockNetwork<SFMTestLevel<String>, String>> networkConstructor =
                BlockNetwork::new;
        BlockNetworkManager<SFMTestLevel<String>, String, BlockNetwork<SFMTestLevel<String>, String>> blockNetworkManager = new BlockNetworkManager<>(
                memberFilterMapper,
                networkConstructor
        );

        // The default block network manager must be empty
        assertTrue(blockNetworkManager.isEmpty());

        // "Place" a cable block
        BlockPos cablePos1 = new BlockPos(0, 0, 0);
        assertNull(testLevel.getBlock(cablePos1));
        testLevel.setBlock(cablePos1, "block 1");
        assertNotNull(testLevel.getBlock(cablePos1));

        // Get the network for the cable block
        BlockNetwork<SFMTestLevel<String>, String> network1 = blockNetworkManager.getOrRegisterNetworkFromMemberPosition(
                testLevel,
                cablePos1
        );
        assertNotNull(network1);
        assertFalse(network1.isEmpty());
        assertEquals(1, network1.size());
        assertTrue(network1.containsBlockPos(cablePos1));
        assertTrue(blockNetworkManager.containsLevel(testLevel));

        // Place another cable block
        BlockPos cablePos2 = new BlockPos(1, 0, 0);
        assertNull(testLevel.getBlock(cablePos2));
        assertTrue(SFMBlockPosUtils.isAdjacent(cablePos1, cablePos2));
        testLevel.setBlock(cablePos2, "block 2");
        assertNotNull(testLevel.getBlock(cablePos2));

        // Get the network at the second cable block to notify
        BlockNetwork<SFMTestLevel<String>, String> network2 = blockNetworkManager.onMemberAddedToLevel(testLevel, cablePos2);
        assertNotNull(network2);
        assertFalse(network2.isEmpty());
        assertTrue(network2.containsBlockPos(cablePos1));
        assertTrue(network2.containsBlockPos(cablePos2));
        assertTrue(blockNetworkManager.containsLevel(testLevel));
        assertEquals(network1, network2);
        assertEquals(2, network2.size());
        assertEquals(System.identityHashCode(network1), System.identityHashCode(network2));
        assertEquals(1, blockNetworkManager.networkCount());

        // Place a third cable in a different chunk
        BlockPos cablePos3 = new BlockPos(-1, 0, 0);
        assertNull(testLevel.getBlock(cablePos3));
        assertTrue(SFMBlockPosUtils.isAdjacent(cablePos1, cablePos3));
        testLevel.setBlock(cablePos3, "block 3");
        assertNotNull(testLevel.getBlock(cablePos3));

        // Get the network at the third cable to notify
        BlockNetwork<SFMTestLevel<String>, String> network3 = blockNetworkManager.onMemberAddedToLevel(testLevel, cablePos3);
        assertNotNull(network3);
        assertFalse(network3.isEmpty());
        assertTrue(network3.containsBlockPos(cablePos1));
        assertTrue(network3.containsBlockPos(cablePos2));
        assertTrue(network3.containsBlockPos(cablePos3));
        assertTrue(blockNetworkManager.containsLevel(testLevel));
        assertEquals(network2, network3);
        assertEquals(3, network3.size());

        // Remove the third cable to unbind the chunk and the network
        testLevel.setBlock(cablePos3, null);
        blockNetworkManager.onMemberRemovedFromLevel(testLevel, cablePos3);

        // Restore the third cable
        testLevel.setBlock(cablePos3, "block 3");
        blockNetworkManager.onMemberAddedToLevel(testLevel, cablePos3);

        // Log state
        System.out.println("Before removal");
        blockNetworkManager.printDebugInfo();

        // Remove the middle cable to fracture the network into two
        testLevel.setBlock(cablePos1, null);
        blockNetworkManager.onMemberRemovedFromLevel(testLevel, cablePos1);
        assertEquals(2, blockNetworkManager.networkCount());
    }

}
