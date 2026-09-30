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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.chat.contents.TranslatableContents;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
        // should be unused input
{% endcase %}
        helper.assertTrue(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                warnings
                        .get(0)
                        .getKey()
{% when '1.21', '1.21.1', '26.1.2' %}
                (
                        (TranslatableContents) warnings
                                .getFirst()
                                .getContents()
                ).getKey()
{% endcase %}
                        .equals(GatherWarningsProgramBehaviour.PROGRAM_WARNING_UNUSED_INPUT_LABEL // should be unused input
                                        .key()
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '26.1.2' %}
                                        .get()), "expected output without matching input warning"
        );
{% when '1.21', '1.21.1' %}
                                        .get()), "expected output without matching input warning");
{% endcase %}
        helper.succeed();
    }

}
