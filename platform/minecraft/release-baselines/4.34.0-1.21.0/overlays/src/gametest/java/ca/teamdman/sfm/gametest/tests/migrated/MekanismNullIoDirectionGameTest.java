package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;

/**
 * Migrated from SFMProgramLinterGameTests.mekanism_null_io_direction
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MekanismNullIoDirectionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.succeed();
        // TODO: Ensure there's a warning when interacting with a mekanism machine without a direction specified
        /*
        INPUT fe:: FROM cube1
        OUTPUT fe:: TO cube2

        should produce a warning

         */
    }
}
