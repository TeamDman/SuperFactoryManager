package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.gametest.LeftRightManagerTest;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;

/**
 * Migrated from SFMWithGameTests.move_without_tag_ingots
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MoveWithoutTagIngotsGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        new LeftRightManagerTest(helper)
                .setProgram("""
                                    EVERY 20 TICKS DO
                                        INPUT WITHOUT NOT TAG ingots FROM left
                                        OUTPUT TO right
                                    END
                                    """)
                .preContents("left", Arrays.asList(
                        new ItemStack(Items.DIRT, 64),
                        new ItemStack(Items.DIRT, 64),
                        new ItemStack(Items.IRON_INGOT, 64),
                        new ItemStack(Items.GOLD_INGOT, 64),
                        new ItemStack(Items.GOLD_NUGGET, 64),
                        new ItemStack(Items.CHEST, 64)
                ))
                .postContents("left", Arrays.asList(
                        new ItemStack(Items.DIRT, 64),
                        new ItemStack(Items.DIRT, 64),
                        ItemStack.EMPTY,
                        ItemStack.EMPTY,
                        new ItemStack(Items.GOLD_NUGGET, 64),
                        new ItemStack(Items.CHEST, 64)
                ))
                .postContents("right", Arrays.asList(
                        new ItemStack(Items.IRON_INGOT, 64),
                        new ItemStack(Items.GOLD_INGOT, 64)
                ))
                .run();
    }
}
