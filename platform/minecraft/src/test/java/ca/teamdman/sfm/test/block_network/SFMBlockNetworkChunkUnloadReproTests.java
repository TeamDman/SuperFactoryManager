package ca.teamdman.sfm.test.block_network;

import ca.teamdman.sfm.common.block_network.BlockNetwork;
import ca.teamdman.sfm.common.block_network.BlockNetworkConstructor;
import ca.teamdman.sfm.common.block_network.BlockNetworkManager;
import ca.teamdman.sfm.common.block_network.BlockNetworkMemberFilterMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SFMBlockNetworkChunkUnloadReproTests {

    @BeforeAll
    static void bootStrapMinecraft() {
        SFMMinecraftBootstrap.bootStrap();
    }

    private static BlockNetworkManager<SFMTestLevel<String>, String, BlockNetwork<SFMTestLevel<String>, String>>
    createManager() {
        BlockNetworkMemberFilterMapper<SFMTestLevel<String>, String> memberFilterMapper = (level, pos) -> {
            String blockString = level.blocks().getFromPosition(pos);
            if (blockString == null) {
                return null;
            } else {
                return "member entity: " + blockString;
            }
        };
        BlockNetworkConstructor<SFMTestLevel<String>, String, BlockNetwork<SFMTestLevel<String>, String>> networkConstructor =
                BlockNetwork::new;
        return new BlockNetworkManager<>(memberFilterMapper, networkConstructor);
    }

    @Test
    public void testClearChunkThenReAddShouldRebuildNetwork() {
        SFMTestLevel<String> testLevel = new SFMTestLevel<>("overworld");
        BlockNetworkManager<SFMTestLevel<String>, String, BlockNetwork<SFMTestLevel<String>, String>> blockNetworkManager =
                createManager();

        // Place a 3-cable line across a chunk boundary:
        // x=14,15 are in chunk 0 and x=16 is in chunk 1.
        BlockPos cablePosChunk0A = new BlockPos(14, 0, 0);
        BlockPos cablePosChunk0B = new BlockPos(15, 0, 0);
        BlockPos cablePosChunk1 = new BlockPos(16, 0, 0);

        testLevel.setBlock(cablePosChunk0A, "block 14");
        testLevel.setBlock(cablePosChunk0B, "block 15");
        testLevel.setBlock(cablePosChunk1, "block 16");

        BlockNetwork<SFMTestLevel<String>, String> network = blockNetworkManager.onMemberAddedToLevel(
            testLevel,
            cablePosChunk0A
        );
        assertNotNull(network);
        blockNetworkManager.onMemberAddedToLevel(testLevel, cablePosChunk0B);
        blockNetworkManager.onMemberAddedToLevel(testLevel, cablePosChunk1);

        assertEquals(3, network.size());
        assertNotNull(blockNetworkManager.getNetwork(testLevel, cablePosChunk1));

        // Simulate unloading the chunk that contains x=16.
        blockNetworkManager.purgeChunk(testLevel, new ChunkPos(cablePosChunk1.getX() >> 4, cablePosChunk1.getZ() >> 4));

        assertEquals(2, network.size());
        assertFalse(network.containsBlockPos(cablePosChunk1));
        assertNull(blockNetworkManager.getNetwork(testLevel, cablePosChunk1));

        // Expected behavior: adding a member in the previously-cleared chunk should rediscover it.
        // Buggy behavior: stale position lookup returns the old network and skips rebuild.
        BlockNetwork<SFMTestLevel<String>, String> rebuiltNetwork = blockNetworkManager.onMemberAddedToLevel(
            testLevel,
            cablePosChunk1
        );

        assertNotNull(rebuiltNetwork);
        assertTrue(rebuiltNetwork.containsBlockPos(cablePosChunk0A));
        assertTrue(rebuiltNetwork.containsBlockPos(cablePosChunk0B));
        assertTrue(rebuiltNetwork.containsBlockPos(cablePosChunk1));
        assertEquals(3, rebuiltNetwork.size());
    }

}
