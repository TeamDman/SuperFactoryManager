package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.linting.GatherWarningsProgramBehaviour;
import ca.teamdman.sfm.common.program.linting.ProblemTracker;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertManagerRunning;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMIfStatementGameTests.count_execution_paths_conditional_2
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class CountExecutionPathsConditional2GameTest extends SFMGameTestDefinition {

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

        // set the labels
        LabelPositionHolder labelPositionHolder = LabelPositionHolder.empty()
                .add("left1", helper.absolutePos(leftPos))
                .add("left2", helper.absolutePos(leftPos))
                .add("right", helper.absolutePos(rightPos))
                .save(manager.getDisk());

        // load the program
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           IF left2 HAS gt 0 stone THEN
                                               INPUT FROM left1
                                           END
                                           IF left1 HAS gt 0 stone THEN
                                               INPUT FROM left2
                                           END
                                           OUTPUT TO right
                                       END
                                   """.stripTrailing().stripIndent());
        assertManagerRunning(manager);
        var program = manager.getProgram();

        // ensure no warnings
        var warnings = DiskItem.getWarnings(manager.getDisk());
        assertTrue(warnings.isEmpty(), "expected 0 warning, got " + warnings.size());

        // count the execution paths
        GatherWarningsProgramBehaviour simulation = new GatherWarningsProgramBehaviour(new ProblemTracker());
        program.tick(ProgramContext.createSimulationContext(
                program,
                labelPositionHolder,
                0,
                simulation
        ));
        List<Integer> expectedPathSizes = new ArrayList<>(List.of(1, 2, 2, 3));
        assertTrue(
                simulation.getSeenPaths().size() == expectedPathSizes.size(),
                "expected " + expectedPathSizes.size() + " execution paths, got " + simulation.getSeenPaths().size()
        );
        int[] actualPathIOSizes = simulation.getSeenIOStatementCountForEachPath();
        // don't assume the order, just that each path size has occurred the specified number of times
        for (int i = 0; i < actualPathIOSizes.length; i++) {
            int pathSize = actualPathIOSizes[i];
            if (!expectedPathSizes.remove((Integer) pathSize)) {
                helper.fail("unexpected path size " + pathSize + " at index " + i + " of " + simulation
                        .getSeenPaths()
                        .size() + " paths");
            }
        }
        helper.succeed();
    }
}
