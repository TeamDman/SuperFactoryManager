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

import java.util.Objects;

/**
 * Ensures mismatched default item output and fluid input produce both warning types.
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class UnusedIoWarningMismatchedItemAndFluidResourceKindsGameTest extends SFMGameTestDefinition {

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
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.MANAGER.get());
        BlockPos leftPos = new BlockPos(2, 2, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());

        ManagerBlockEntity manager = helper.getBlockEntity(new BlockPos(1, 2, 0), ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        LabelPositionHolder.empty()
                .add("left", helper.absolutePos(leftPos))
                .save(Objects.requireNonNull(manager.getDisk()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT fluid:: FROM left
                                           OUTPUT TO left
                                       END
                                   """.stripTrailing().stripIndent());
        helper.assertManagerRunning(manager);

        var warnings = DiskItem.getWarnings(Objects.requireNonNull(manager.getDisk()));
        helper.assertTrue(warnings.size() == 2, "expected 2 warnings, got " + warnings.size());

        String outputKey = GatherWarningsProgramBehaviour.PROGRAM_WARNING_OUTPUT_RESOURCE_TYPE_NOT_FOUND_IN_INPUTS
                .key()
                .get();
        String inputKey = GatherWarningsProgramBehaviour.PROGRAM_WARNING_UNUSED_INPUT_LABEL.key().get();

        boolean foundOutputMismatch = false;
        boolean foundUnusedFluidInput = false;

        for (TranslatableContents warning : warnings) {
            if (warning.getKey().equals(outputKey)
                && warning.getArgs().length >= 3
                && warning.getArgs()[0].equals("OUTPUT TO left")
                && warning.getArgs()[2].equals("sfm:item")) {
                foundOutputMismatch = true;
            }
            if (warning.getKey().equals(inputKey)
                && warning.getArgs().length >= 5
                && warning.getArgs()[0].equals("INPUT fluid:: FROM left")
                && warning.getArgs()[2].equals("sfm:fluid")
                && warning.getArgs()[3].equals("left")
                && warning.getArgs()[4].equals("sfm:fluid")) {
                foundUnusedFluidInput = true;
            }
        }

        helper.assertTrue(foundOutputMismatch, "expected warning for output item without item input");
        helper.assertTrue(foundUnusedFluidInput, "expected warning for input fluid without fluid output");

        helper.succeed();
    }
}
