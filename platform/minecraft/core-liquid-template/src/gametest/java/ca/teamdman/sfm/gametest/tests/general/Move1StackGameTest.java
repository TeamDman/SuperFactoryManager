package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.gametest.LeftRightManagerTest;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.Collections;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
@SFMGameTest
public class Move1StackGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var test = new LeftRightManagerTest(helper);
        test.setProgram("""
                                    EVERY 20 TICKS DO
                                        INPUT FROM left
                                        OUTPUT TO right
                                    END
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
