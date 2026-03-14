package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;


/**
 * Migrated from SFMProgramLinterGameTests.disk_name
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class DiskNameGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "1x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos chestPos = new BlockPos(0, 2, 0);
        helper.setBlock(chestPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        var chest = helper.getItemHandler(chestPos);

        {
            ItemStack disk = new ItemStack(SFMItems.DISK_ITEM.get());
            String programString = """
                    NAME "bruh"
                    EVERY 20 TICKS DO
                    END
                    """;
            DiskItem.setProgram(disk, programString);
            DiskItem.rebuildSfmlProgram(disk, null, true);
            chest.insertItem(0, disk, false);
            assertTrue(DiskItem.getProgramName(disk).equals("bruh"), "program name should be bruh for disk 1");
            assertTrue(DiskItem.getWarnings(disk).isEmpty(), "there should be no warnings on disk 1");
            assertTrue(DiskItem.getErrors(disk).isEmpty(), "there should be no errors on disk 1");
            assertTrue(disk.getHoverName().getString().equals("bruh"), "display name should be \"bruh\" for disk 1");
        }
        {
            ItemStack disk = new ItemStack(SFMItems.DISK_ITEM.get());
            String programString = """
                    EVERY 20 TICKS DO
                    END
                    """;
            DiskItem.setProgram(disk, programString);
            DiskItem.rebuildSfmlProgram(disk, null, true);
            chest.insertItem(1, disk, false);
            assertTrue(DiskItem.getProgramName(disk).isEmpty(), "program name should be empty for disk 2");
            assertTrue(DiskItem.getWarnings(disk).isEmpty(), "there should be no warnings on disk 2");
            assertTrue(DiskItem.getErrors(disk).isEmpty(), "there should be no errors on disk 2");
            assertTrue(
                    disk.getHoverName().contains(LocalizationKeys.DISK_ITEM.getComponent()),
                    "display name should be default for disk 2"
            );
        }
        helper.succeed();
    }
}
