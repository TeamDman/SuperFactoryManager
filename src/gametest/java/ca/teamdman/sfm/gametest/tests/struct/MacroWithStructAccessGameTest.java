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
 * Tests that macro expansion works with struct access using the USING keyword.
 * This tests the "machine using field" syntax within macros.
 */
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
@SFMGameTest
public class MacroWithStructAccessGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var test = new LeftRightManagerTest(helper);
        test.setProgram("""
                NAME "Macro with Struct Access Test"

                protocol Container
                    main: sidequalifier slotqualifier
                end

                struct Chest : Container
                    main: EACH SIDE SLOTS 0-26
                end

                macro process(machine: Container, source, dest)
                    input from source
                    output to machine using main
                    forget
                    input from machine using main
                    output to dest
                end

                let left = Chest

                every 20 ticks do
                    do process(left, left, right)
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
