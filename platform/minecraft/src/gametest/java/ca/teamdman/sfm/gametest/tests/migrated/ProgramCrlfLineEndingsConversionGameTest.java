package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Migrated from SFMCorrectnessGameTests.program_crlf_line_endings_conversion
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class ProgramCrlfLineEndingsConversionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "1x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var managerPos = new BlockPos(0, 2, 0);
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        String program = """
                NAME "line endings test"
                EVERY 20 TICKS DO
                    INPUT FROM a
                    OUTPUT TO b
                END
                """.stripTrailing().stripIndent();
        String programWithWindowsLineEndings = program.replaceAll("\n", "\r\n");
        manager.setProgram(programWithWindowsLineEndings);
        var programString = manager.getProgramString();
        if (programString.equals(program)) {
            helper.succeed();
        } else {
            helper.fail(String.format(
                    "program string was not converted correctly: %s",
                    programString
            ));
        }
    }
}
