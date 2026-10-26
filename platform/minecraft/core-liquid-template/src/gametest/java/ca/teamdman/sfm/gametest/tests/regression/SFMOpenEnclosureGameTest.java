package ca.teamdman.sfm.gametest.tests.regression;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Runtime witness that SFM's automatic side wall is absent. */
@SFMGameTest
@MCVersionDependentBehaviour
public final class SFMOpenEnclosureGameTest extends SFMGameTestDefinition {
    @Override public String template() { return "3x3x3"; }
    @Override public String testName() { return "sfm_open_enclosure"; }

    @Override public void run(SFMGameTestHelper helper) {
        BlockPos outsideWestWall = new BlockPos(-1, 1, 1);
        helper.assertTrue(!helper.getBlockState(outsideWestWall).is(Blocks.BARRIER),
                "SFM GameTest still has an automatically generated barrier wall");
        BlockPos explicitBarrier = new BlockPos(1, 1, 1);
        helper.setBlock(explicitBarrier, Blocks.BARRIER);
        helper.assertTrue(helper.getBlockState(explicitBarrier).is(Blocks.BARRIER),
                "Explicit fixture barrier was removed");
        helper.succeed();
    }
}
