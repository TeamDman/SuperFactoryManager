package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.linting.GatherWarningsProgramBehaviour;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;


/**
 * Migrated from SFMIfStatementGameTests.count_execution_paths_conditional_1b
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class CountExecutionPathsConditional1bGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {

        return "3x2x1";
    }

    @Override
    public String batchName() {

        return "linting";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // place inventories
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.MANAGER.get());
        BlockPos rightPos = new BlockPos(0, 2, 0);
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        BlockPos leftPos = new BlockPos(2, 2, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());

        // place manager
        ManagerBlockEntity manager = helper.getBlockEntity(new BlockPos(1, 2, 0), ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        // set the labels
        LabelPositionHolder.empty()
                .add("left", helper.absolutePos(leftPos))
                .save(manager.getDisk());

        // load the program
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           IF left HAS gt 0 stone THEN
                                               INPUT FROM left
                                           END
                                       END
                                   """.stripTrailing().stripIndent());
        helper.assertManagerRunning(manager);

        // assert expected warnings
        var warnings = DiskItem.getWarnings(manager.getDisk());
        helper.assertTrue(warnings.size() == 1, "expected 1 warning, got " + warnings.size());
        helper.assertTrue(
                (
                        (TranslatableContents) warnings
                                .getFirst()
                                .getContents()
                ).getKey()
                        .equals(GatherWarningsProgramBehaviour.PROGRAM_WARNING_UNUSED_INPUT_LABEL // should be unused input
                                        .key()
                                        .get()), "expected output without matching input warning");
        helper.succeed();
    }

}
