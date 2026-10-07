package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.mekanism_sidedness_linter %}
import ca.teamdman.sfm.gametest.tests.compat.mekanism.MekanismSidednessLinterGameTestGenerator;
{% endif %}
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}

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
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.mekanism_sidedness_linter %}
        MekanismSidednessLinterGameTestGenerator.check(
                helper, "fe::", "", "", false, false, true, true
        );
{% else %}
        helper.succeed();
        // TODO: Ensure there's a warning when interacting with a mekanism machine without a direction specified
        /*
        INPUT fe:: FROM cube1
        OUTPUT fe:: TO cube2

        should produce a warning

         */
{% endif %}
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        helper.succeed();
        // TODO: Ensure there's a warning when interacting with a mekanism machine without a direction specified
        /*
        INPUT fe:: FROM cube1
        OUTPUT fe:: TO cube2

        should produce a warning

         */
{% endcase %}
    }
}
