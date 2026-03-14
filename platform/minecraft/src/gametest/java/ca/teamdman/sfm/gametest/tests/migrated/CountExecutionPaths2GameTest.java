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

import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertManagerRunning;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.count_execution_paths_2
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class CountExecutionPaths2GameTest extends SFMGameTestDefinition {

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
                .add("left", helper.absolutePos(leftPos))
                .add("right", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        // load the program
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT FROM left
                                           OUTPUT TO right
                                       END
                                       EVERY 20 TICKS DO
                                           INPUT FROM left
                                           OUTPUT TO right
                                           OUTPUT TO right
                                       END
                                   """.stripTrailing().stripIndent());
        assertManagerRunning(manager);
        var program = manager.getProgram();

        // ensure no warnings
        var warnings = DiskItem.getWarnings(Objects.requireNonNull(manager.getDisk()));
        assertTrue(warnings.isEmpty(), "expected 0 warning, got " + warnings.size());

        // count the execution paths
        GatherWarningsProgramBehaviour simulation = new GatherWarningsProgramBehaviour(new ProblemTracker());
        program.tick(ProgramContext.createSimulationContext(
                program,
                labelPositionHolder,
                0,
                simulation
        ));
        assertTrue(simulation.getSeenPaths().size() == 2, "expected single execution path");
        assertTrue(simulation.getSeenPaths().get(0).history().size() == 2, "expected two elements in execution path");
        assertTrue(simulation.getSeenPaths().get(1).history().size() == 3, "expected two elements in execution path");
        helper.succeed();
    }
}
