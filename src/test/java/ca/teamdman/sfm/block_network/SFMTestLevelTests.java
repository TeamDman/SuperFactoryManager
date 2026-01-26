package ca.teamdman.sfm.block_network;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SFMTestLevelTests {
    @Test
    public void testLevelCanBeMapKey() {
        SFMTestLevel<String> level = new SFMTestLevel<>("overworld");
        var hashCode1 = level.hashCode();
        level.setBlock(new BlockPos(0,0,0), "cobblestone");
        var hashCode2 = level.hashCode();
        assertEquals(hashCode1, hashCode2);
    }
}
