package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.linting.compat.mekanism.MekanismSidednessProgramLinter;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.*;
import ca.teamdman.sfml.ast.IOStatement;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Exercises registered linters through the same manager/disk path used in game. */
@SFMGameTestGenerator
public class MekanismSidednessLinterGameTestGenerator extends SFMGameTestGeneratorBase {
    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> tests) {
        for (String resource : List.of("fe", "item", "fluid", "infusion")) {
            add(tests, resource + "_implicit_null", resource + "::", "", "", false, false, true, true);
            add(tests, resource + "_explicit_null", resource + "::", " NULL SIDE", " NULL SIDE", false, false, true, true);
        }
        add(tests, "input_only_null", "fe::", "", " TOP SIDE", false, false, true, false);
        add(tests, "output_only_null", "fe::", " NORTH SIDE", "", false, false, false, true);
        add(tests, "physical_sides", "fe::", " NORTH SIDE", " TOP SIDE", false, false, false, false);
        add(tests, "mixed_null_and_physical", "fe::", " NULL, NORTH SIDE", " TOP, NULL SIDE", false, false, true, true);
        add(tests, "non_mekanism_null", "item::", "", "", true, false, false, false);
        add(tests, "read_only_condition_null", "fe::", " NORTH SIDE", " TOP SIDE", false, true, false, false);
    }

    private static void add(Consumer<SFMGameTestDefinition> tests, String name, String resource,
                            String inputSide, String outputSide, boolean vanilla, boolean condition,
                            boolean warnInput, boolean warnOutput) {
        tests.accept(new SFMGameTestDefinition() {
            @Override public String template() { return "3x2x1"; }
            @Override public String batchName() { return "linting"; }
            @Override public String testName() { return "mekanism_sidedness_linter_" + name; }
            @Override public void run(SFMGameTestHelper helper) {
                check(helper, resource, inputSide, outputSide, vanilla, condition, warnInput, warnOutput);
            }
        });
    }

    public static void check(SFMGameTestHelper helper, String resource, String inputSide, String outputSide,
                             boolean vanilla, boolean condition, boolean warnInput, boolean warnOutput) {
        BlockPos inputPos = new BlockPos(0, 2, 0);
        BlockPos managerPos = new BlockPos(1, 2, 0);
        BlockPos outputPos = new BlockPos(2, 2, 0);
        var block = vanilla ? SFMBlocks.TEST_BARREL.get() : MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock();
        helper.setBlock(inputPos, block);
        helper.setBlock(outputPos, block);
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        LabelPositionHolder.empty()
                .add("source", helper.absolutePos(inputPos))
                .add("destination", helper.absolutePos(outputPos))
                .save(Objects.requireNonNull(manager.getDisk()));
        String io = "INPUT " + resource + " FROM source" + inputSide + "\n"
                    + "OUTPUT " + resource + " TO destination" + outputSide + "\n";
        manager.setProgram("EVERY 20 TICKS DO\n"
                           + (condition ? "IF source HAS GT 0 fe:: THEN\n" : "")
                           + io + (condition ? "END\n" : "") + "END");
        helper.assertManagerRunning(manager);
        String key = MekanismSidednessProgramLinter.PROGRAM_WARNING_MEKANISM_USED_WITH_NULL_DIRECTION.key().get();
        List<TranslatableContents> warnings = DiskItem.getWarnings(manager.getDisk()).stream()
                .filter(warning -> warning.getKey().equals(key)).toList();
        int expected = (warnInput ? 1 : 0) + (warnOutput ? 1 : 0);
        helper.assertTrue(warnings.size() == expected,
                          "Expected " + expected + " null-side warnings, got " + warnings);
        List<IOStatement> statements = Objects.requireNonNull(manager.getProgram()).getDescendantStatements()
                .filter(IOStatement.class::isInstance).map(IOStatement.class::cast).toList();
        helper.assertTrue(statements.size() == 2, "Fixture must contain both INPUT and OUTPUT");
        if (warnInput) assertWarning(helper, warnings, "source", statements.get(0).toStringPretty());
        if (warnOutput) assertWarning(helper, warnings, "destination", statements.get(1).toStringPretty());
        helper.succeed();
    }

    private static void assertWarning(SFMGameTestHelper helper, List<TranslatableContents> warnings,
                                      String label, String statement) {
        helper.assertTrue(warnings.stream().anyMatch(warning -> warning.getArgs().length == 2
                        && warning.getArgs()[0].toString().equals(label)
                        && warning.getArgs()[1].toString().equals(statement)),
                "Missing exact warning label/statement: " + label + " / " + statement);
    }
}
