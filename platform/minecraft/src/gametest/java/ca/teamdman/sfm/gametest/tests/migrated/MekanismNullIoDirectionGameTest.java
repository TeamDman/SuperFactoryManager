package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfm.gametest.tests.compat.mekanism.MekanismSidednessLinterGameTestGenerator;

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
        MekanismSidednessLinterGameTestGenerator.check(
                helper, "fe::", "", "", false, false, true, true
        );
    }
}
