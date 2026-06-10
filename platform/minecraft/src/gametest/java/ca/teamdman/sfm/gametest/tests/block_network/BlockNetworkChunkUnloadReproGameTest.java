package ca.teamdman.sfm.gametest.tests.block_network;

import ca.teamdman.sfm.common.block_network.BlockNetwork;
import ca.teamdman.sfm.common.block_network.BlockNetworkConstructor;
import ca.teamdman.sfm.common.block_network.BlockNetworkManager;
import ca.teamdman.sfm.common.block_network.BlockNetworkMemberFilterMapper;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

@SFMGameTest
public class BlockNetworkChunkUnloadReproGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        SFMGameTestLevel<String> testLevel = new SFMGameTestLevel<>("overworld");
        BlockNetworkManager<SFMGameTestLevel<String>, String, BlockNetwork<SFMGameTestLevel<String>, String>> blockNetworkManager =
                createManager();

        BlockPos cablePosChunk0A = new BlockPos(14, 0, 0);
        BlockPos cablePosChunk0B = new BlockPos(15, 0, 0);
        BlockPos cablePosChunk1 = new BlockPos(16, 0, 0);

        testLevel.setBlock(cablePosChunk0A, "block 14");
        testLevel.setBlock(cablePosChunk0B, "block 15");
        testLevel.setBlock(cablePosChunk1, "block 16");

        BlockNetwork<SFMGameTestLevel<String>, String> network = blockNetworkManager.onMemberAddedToLevel(
                testLevel,
                cablePosChunk0A
        );
        assertNotNull(helper, network, "Initial network should be created");
        blockNetworkManager.onMemberAddedToLevel(testLevel, cablePosChunk0B);
        blockNetworkManager.onMemberAddedToLevel(testLevel, cablePosChunk1);

        assertEquals(helper, 3, network.size(), "Initial network size");
        assertNotNull(helper, blockNetworkManager.getNetwork(testLevel, cablePosChunk1), "Chunk one cable should be tracked");

        blockNetworkManager.purgeChunk(testLevel, chunkPos(cablePosChunk1));

        assertEquals(helper, 2, network.size(), "Network size after chunk purge");
        helper.assertTrue(!network.containsBlockPos(cablePosChunk1), "Purged network should not contain chunk one cable");
        assertNull(helper, blockNetworkManager.getNetwork(testLevel, cablePosChunk1), "Chunk one cable lookup should be removed");

        BlockNetwork<SFMGameTestLevel<String>, String> rebuiltNetwork = blockNetworkManager.onMemberAddedToLevel(
                testLevel,
                cablePosChunk1
        );

        assertNotNull(helper, rebuiltNetwork, "Rebuilt network should be present");
        helper.assertTrue(rebuiltNetwork.containsBlockPos(cablePosChunk0A), "Rebuilt network should contain first chunk zero cable");
        helper.assertTrue(rebuiltNetwork.containsBlockPos(cablePosChunk0B), "Rebuilt network should contain second chunk zero cable");
        helper.assertTrue(rebuiltNetwork.containsBlockPos(cablePosChunk1), "Rebuilt network should contain chunk one cable");
        assertEquals(helper, 3, rebuiltNetwork.size(), "Rebuilt network size");

        helper.succeed();
    }

    private static BlockNetworkManager<SFMGameTestLevel<String>, String, BlockNetwork<SFMGameTestLevel<String>, String>>
    createManager() {
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
        return new BlockNetworkManager<>(memberFilterMapper, networkConstructor);
    }

    private static ChunkPos chunkPos(BlockPos blockPos) {
        return new ChunkPos(blockPos.getX() >> 4, blockPos.getZ() >> 4);
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
            int expected,
            int actual,
            String message
    ) {
        helper.assertTrue(
                expected == actual,
                message + ": expected " + expected + " but got " + actual
        );
    }
}
