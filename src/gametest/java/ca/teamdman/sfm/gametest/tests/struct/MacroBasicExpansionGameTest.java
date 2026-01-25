package ca.teamdman.sfm.gametest.tests.struct;

import ca.teamdman.sfm.gametest.LeftRightManagerTest;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.Collections;

/**
 * Tests that macro expansion works at runtime.
 * The macro transfers items from 'left' to 'right' using the expand statement.
 */
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
@SFMGameTest
public class MacroBasicExpansionGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var test = new LeftRightManagerTest(helper);
        test.setProgram("""
                NAME "Macro Basic Expansion Test"

                macro transfer(source, dest)
                    input from source
                    output to dest
                end

                every 20 ticks do
                    do transfer(left, right)
                end
                """);
        test.preContents("left", Arrays.asList(
                new ItemStack(Blocks.DIRT, 64)
        ));
        test.postContents("left", Collections.emptyList());
        test.postContents("right", Arrays.asList(
                new ItemStack(Blocks.DIRT, 64)
        ));
        test.run();
    }
}
