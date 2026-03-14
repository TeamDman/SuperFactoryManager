package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.net.ServerboundOutputInspectionRequestPacket;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfml.ast.OutputStatement;
import ca.teamdman.sfml.ast.SFMLProgram;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.*;

/**
 * Migrated from SFMIfStatementGameTests.conditional_output_inspection
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class ConditionalOutputInspectionGameTest extends SFMGameTestDefinition {

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
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.MANAGER_BLOCK.get());
        BlockPos rightPos = new BlockPos(0, 2, 0);
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        BlockPos leftPos = new BlockPos(2, 2, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());

        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        leftChest.insertItem(0, new ItemStack(Blocks.DIRT, 64), false);

        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));


        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(manager.getDisk());

        // set the program
        String code = """
                    EVERY 20 TICKS DO
                        IF a HAS = 64 dirt THEN
                            INPUT RETAIN 32 FROM a
                        END
                        OUTPUT TO b
                    END
                """.stripTrailing().stripIndent();
        manager.setProgram(code);
        assertManagerRunning(manager);

        // compile a new program for inspection
        SFMLProgram program = compile(code);


        OutputStatement outputStatement = (OutputStatement) program
                .triggers()
                .get(0)
                .getBlock()
                .getChildNodes()
                .get(1);

        String inspectionResults = ServerboundOutputInspectionRequestPacket.getOutputStatementInspectionResultsString(
                manager,
                program,
                outputStatement
        );

        //noinspection TrailingWhitespacesInTextBlock
        String expected = """
                OUTPUT TO b
                -- predictions may differ from actual execution results
                -- POSSIBILITY 0 -- all false
                OVERALL a HAS = 64 dirt -- false
                                
                    -- predicted inputs:
                    none
                    -- predicted outputs:
                    none
                -- POSSIBILITY 1 -- all true
                OVERALL a HAS = 64 dirt -- true
                                
                    -- predicted inputs:
                    INPUT 32 minecraft:dirt FROM a SLOTS 0
                    -- predicted outputs:
                    OUTPUT 32 minecraft:dirt TO b
                """.stripLeading().stripIndent().stripTrailing();
        if (!inspectionResults.equals(expected)) {
            System.out.println("Received results:");
            System.out.println(inspectionResults);
            System.out.println("Expected:");
            System.out.println(expected);

            // get the position of the difference and show it
            for (int i = 0; i < inspectionResults.length(); i++) {
                if (inspectionResults.charAt(i) != expected.charAt(i)) {
                    System.out.println("Difference at position "
                                       + i
                                       + ":"
                                       + inspectionResults.charAt(i)
                                       + " vs "
                                       + expected.charAt(i));
                    break;
                }
            }

            helper.fail("inspection didn't match results");
        }

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            assertTrue(leftChest.getStackInSlot(0).getCount() == 32, "Dirt did not depart");
            assertTrue(rightChest.getStackInSlot(0).getCount() == 32, "Dirt did not arrive");
        });
    }
}
