package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertManagerRunning;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.unused_io_warning_input_label_not_present_in_output
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class UnusedIoWarningInputLabelNotPresentInOutputGameTest extends SFMGameTestDefinition {

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
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.MANAGER_BLOCK.get());
        BlockPos rightPos = new BlockPos(0, 2, 0);
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        BlockPos leftPos = new BlockPos(2, 2, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());

        // place manager
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        LabelPositionHolder.empty()
                .add("left", helper.absolutePos(leftPos))
                .save(Objects.requireNonNull(manager.getDisk()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT FROM left
                                       END
                                   """.stripTrailing().stripIndent());
        assertManagerRunning(manager);

        // assert expected warnings
        var warnings = DiskItem.getWarnings(Objects.requireNonNull(manager.getDisk()));
        assertTrue(warnings.size() == 1, "expected 1 warning, got " + warnings.size());

        TranslatableContents firstWarning = warnings.get(0);
        String expectedKey = LocalizationKeys.PROGRAM_WARNING_UNUSED_INPUT_LABEL.key().get();
        assertTrue(firstWarning.getKey().equals(expectedKey), "expected output without matching input warning");
        assertTrue(firstWarning.getArgs().length == 5, "expected 5 arguments in warning");
        assertTrue(firstWarning.getArgs()[0].equals("INPUT FROM left"), "expected arg 0 to be \"INPUT FROM left\"");
        assertTrue(firstWarning.getArgs()[1].equals("Line 2, Column 4"), "expected arg 1 to be \"Line 2, Column 4\"");
        assertTrue(firstWarning.getArgs()[2].equals("sfm:item"), "expected arg 2 to be \"sfm:item\"");
        assertTrue(firstWarning.getArgs()[3].equals("left"), "expected arg 3 to be \"left\"");
        assertTrue(firstWarning.getArgs()[4].equals("sfm:item"), "expected arg 4 to be \"sfm:item\"");
        helper.succeed();
    }
}
