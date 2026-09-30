package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.core.component.DataComponents;
{% endcase %}
import net.minecraft.world.item.ItemStack;




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
        helper.setBlock(chestPos, SFMBlocks.TEST_BARREL.get());
        var chest = helper.getItemHandler(chestPos);

        {
            ItemStack disk = new ItemStack(SFMItems.DISK.get());
            String programString = """
                    NAME "bruh"
                    EVERY 20 TICKS DO
                    END
                    """;
            DiskItem.setProgram(disk, programString);
            DiskItem.compileAndUpdateErrorsAndWarnings(disk, null, true);
            chest.insertItem(0, disk, false);
            helper.assertTrue(DiskItem.getProgramName(disk).equals("bruh"), "program name should be bruh for disk 1");
            helper.assertTrue(DiskItem.getWarnings(disk).isEmpty(), "there should be no warnings on disk 1");
            helper.assertTrue(DiskItem.getErrors(disk).isEmpty(), "there should be no errors on disk 1");
            helper.assertTrue(
                    disk.getHoverName().getString().equals("bruh"),
                    "display name should be \"bruh\" for disk 1"
            );
        }
        {
            ItemStack disk = new ItemStack(SFMItems.DISK.get());
            String programString = """
                    EVERY 20 TICKS DO
                    END
                    """;
            DiskItem.setProgram(disk, programString);
            DiskItem.compileAndUpdateErrorsAndWarnings(disk, null, true);
            chest.insertItem(1, disk, false);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            helper.assertTrue(DiskItem.getProgramName(disk).isEmpty(), "program name should be empty for disk 2");
{% when '26.1.2' %}
            helper.assertTrue(disk.getComponentsPatch().getPatch(DataComponents.ITEM_NAME) == null, "program name should be empty for disk 2");
{% endcase %}
            helper.assertTrue(DiskItem.getWarnings(disk).isEmpty(), "there should be no warnings on disk 2");
            helper.assertTrue(DiskItem.getErrors(disk).isEmpty(), "there should be no errors on disk 2");
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            helper.assertTrue(
                    disk.getHoverName().contains(DiskItem.DISK_ITEM.getComponent()),
                    "display name should be default for disk 2"
            );
{% when '26.1.2' %}
            helper.assertTrue(disk.getHoverName().contains(DiskItem.DISK_ITEM.getComponent()), "display name should be default for disk 2");
{% endcase %}
        }
        helper.succeed();
    }

}
