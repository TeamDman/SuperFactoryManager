package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.gametest.LeftRightManagerTest;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;

/**
 * Migrated from SFMWithGameTests.move_one_each_with_tag_mineable_axe
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MoveOneEachWithTagMineableAxeGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        new LeftRightManagerTest(helper)
                .setProgram("""
                                    EVERY 20 TICKS DO
                                        INPUT FROM left
                                        OUTPUT 1 EACH WITH TAG minecraft:mineable/axe TO right
                                    END
                                    """)
                .preContents("left", Arrays.asList(
                        new ItemStack(Items.OAK_LOG, 64),
                        new ItemStack(Items.BIRCH_LOG, 64),
                        new ItemStack(Items.SPRUCE_LOG, 64)
                ))
                .postContents("left", Arrays.asList(
                        new ItemStack(Items.OAK_LOG, 63),
                        new ItemStack(Items.BIRCH_LOG, 63),
                        new ItemStack(Items.SPRUCE_LOG, 63)
                ))
                .postContents("right", Arrays.asList(
                        new ItemStack(Items.OAK_LOG, 1),
                        new ItemStack(Items.BIRCH_LOG, 1),
                        new ItemStack(Items.SPRUCE_LOG, 1)
                ))
                .run();
    }
}
