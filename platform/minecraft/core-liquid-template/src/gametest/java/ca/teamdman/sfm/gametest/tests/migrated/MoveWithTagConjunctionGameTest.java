package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.gametest.LeftRightManagerTest;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;

/**
 * Migrated from SFMWithGameTests.move_with_tag_conjunction
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MoveWithTagConjunctionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        new LeftRightManagerTest(helper)
                .setProgram("""
                                    EVERY 20 TICKS DO
                                        INPUT WITH TAG minecraft:planks AND TAG minecraft:mineable/axe FROM left
                                        OUTPUT TO right
                                    END
                                    """)
                .preContents("left", Arrays.asList(
                        new ItemStack(Items.DIRT, 64),      // Slot 0
                        new ItemStack(Items.STONE, 64),     // Slot 1
                        new ItemStack(Items.OAK_PLANKS, 64) // Slot 2
                ))
                .postContents("left", Arrays.asList(
                        new ItemStack(Items.DIRT, 64),     // Slot 0 (Dirt remains)
                        new ItemStack(Items.STONE, 64),    // Slot 1 (Stone remains)
                        ItemStack.EMPTY                    // Slot 2 (Planks moved)
                ))
                .postContents("right", Arrays.asList(
                        new ItemStack(Items.OAK_PLANKS, 64) // Slot 0
                ))
                .run();
    }
}
