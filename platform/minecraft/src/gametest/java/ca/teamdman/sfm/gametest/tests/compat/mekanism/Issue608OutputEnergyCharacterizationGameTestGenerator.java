package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.program.*;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.gametest.*;
import ca.teamdman.sfml.ast.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static ca.teamdman.sfml.ast.ResourceQuantity.IdExpansionBehaviour.NO_EXPAND;

/**
 * Characterizes current output-side loss, not a regression asserting a fix.
 * The standalone pinned Mekanism adapter probe establishes two-FE insertion granularity.
 * These reduced handlers exercise real SFM transfer/tracker/logging code on the baseline.
 * They do not claim to instantiate Applied Flux or the reporter's complete modpack.
 */
@SFMGameTestGenerator
public class Issue608OutputEnergyCharacterizationGameTestGenerator extends SFMGameTestGeneratorBase {
    private record Scenario(String name, int sourceAmount, int extractionLimit, int initialDestination,
                            int capacity, long quantity, long retention, boolean rejectFirst,
                            boolean allQuantized, int expectedFirst, int expectedLater, int expectedLoss) {}

    @Override public void generateTests(Consumer<SFMGameTestDefinition> tests) {
        long max = Long.MAX_VALUE;
        for (var scenario : List.of(
                new Scenario("report_retention_101", 500000, 500000, 38900, 500000, max, 39001, false, false, 100, 99, 1),
                new Scenario("retention_100_control", 500000, 500000, 38900, 500000, max, 39000, false, false, 100, 99, 0),
                new Scenario("partial_extraction_101", 500000, 101, 38900, 500000, max, max, false, false, 100, 99, 1),
                new Scenario("output_quantity_one", 1000, 1000, 0, 1000, 1, max, false, false, 0, 99, 1),
                new Scenario("zero_simulation_continues", 1000, 1000, 0, 1000, max, max, true, false, 0, 99, 0),
                new Scenario("one_fe_source_continues", 1, 1, 0, 1000, max, max, false, false, 0, 1, 0),
                new Scenario("unrestricted_greedy_control", 1000, 1000, 0, 1000, max, max, false, false, 1000, 0, 0),
                new Scenario("ten_outputs_one_fe_each", 1000, 1000, 0, 1000, 1, max, false, true, 0, 0, 10)
        )) tests.accept(new OutputTest(scenario));
    }

    private static class OutputTest extends SFMGameTestDefinition {
        private final Scenario scenario;
        OutputTest(Scenario scenario) { this.scenario = scenario; }
        @Override public String template() { return "3x2x1"; }
        @Override public String testName() { return "issue_608_output_" + scenario.name(); }

        @Override public void run(SFMGameTestHelper helper) {
            var managerPos = new BlockPos(1, 2, 0);
            helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
            var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
            manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
            manager.setProgram("EVERY 20 TICKS DO\nEND");
            helper.assertManagerRunning(manager);
            var context = new ProgramContext(Objects.requireNonNull(manager.getProgram()), manager, new ExecuteProgramBehaviour());
            context.getLogger().setLogLevel(Level.ERROR);
            context.getLogger().clear();
            var type = SFMResourceTypes.FORGE_ENERGY.get();
            var ids = new ResourceIdSet(List.of(new ResourceIdentifier<>("sfm", "forge_energy", ".*", ".*")));
            var source = new EnergyStorage(scenario.sourceAmount(), scenario.sourceAmount(), scenario.extractionLimit());
            source.receiveEnergy(scenario.sourceAmount(), false);
            var input = new LimitedInputSlot<Integer, Class<Integer>, IEnergyStorage>(
                    new Label("source"), helper.absolutePos(managerPos), null, 0, source,
                    new ResourceLimit(ids, Limit.MAX_QUANTITY_NO_RETENTION, With.ALWAYS_TRUE).createInputTracker(ResourceIdSet.EMPTY),
                    source.getEnergyStored(), type);
            var first = new QuantizedReceiver(scenario.capacity(), scenario.initialDestination(), scenario.rejectFirst());
            var firstSlot = output(first, ids, scenario.quantity(), scenario.retention(), helper.absolutePos(managerPos));
            OutputStatement.moveTo(context, input, firstSlot);
            helper.assertTrue(first.getEnergyStored() - scenario.initialDestination() == scenario.expectedFirst(),
                    "Unexpected first destination amount for " + scenario.name());
            if (scenario.expectedFirst() < scenario.sourceAmount()) {
                helper.assertTrue(!input.isDone(), "First destination unexpectedly ended source iteration");
            }

            int laterMoved = 0;
            for (int i = 0; i < 9; i++) {
                EnergyStorage next = scenario.allQuantized()
                        ? new QuantizedReceiver(1000, 0, false) : new EnergyStorage(1000, 11, 1000);
                var nextSlot = output(next, ids, scenario.allQuantized() ? 1 : Long.MAX_VALUE,
                        Long.MAX_VALUE, helper.absolutePos(managerPos));
                // Mirrors the outer output loop's guard, preserving destination order.
                if (!input.isDone() && !nextSlot.isDone()) OutputStatement.moveTo(context, input, nextSlot);
                laterMoved += next.getEnergyStored();
                if (scenario.expectedLater() == 99) {
                    helper.assertTrue(next.getEnergyStored() == 11, "Later destination " + i + " was skipped");
                }
            }
            helper.assertTrue(laterMoved == scenario.expectedLater(), "Unexpected later-destination progress");
            int loss = scenario.sourceAmount() - source.getEnergyStored() - scenario.expectedFirst() - laterMoved;
            helper.assertTrue(loss == scenario.expectedLoss(), "Unexpected conservation deficit: " + loss);
            List<String> reports = new ArrayList<>();
            for (var event : context.getLogger().getLogs()) {
                for (var arg : event.contents().getArgs()) {
                    if (arg instanceof String text && text.startsWith("!!!RESOURCE LOSS HAS OCCURRED!!!")) reports.add(text);
                }
            }
            helper.assertTrue(reports.size() == scenario.expectedLoss(), "Expected one report per lost FE in these fixtures");
            if (scenario.name().equals("report_retention_101")) {
                String report = reports.get(0);
                for (String line : List.of(
                        "Simulated extraction\\s+: 500000",
                        "Simulated insertion remainder\\s+: 38900 \\(moved=461100\\)",
                        "Actual extraction\\s+: 101",
                        "Actual insertion\\s+: 100 class java.lang.Integer",
                        "Actual insertion remainder\\s+: 1 \\(sfm:forge_energy:class java.lang.Integer\\)")) {
                    helper.assertTrue(Pattern.compile(line).matcher(report).find(), "Report differs from issue #608: " + line);
                }
            }
            helper.succeed();
        }
    }

    private static LimitedOutputSlot<Integer, Class<Integer>, IEnergyStorage> output(
            EnergyStorage handler, ResourceIdSet ids, long quantity, long retention, BlockPos pos) {
        var limit = new Limit(new ResourceQuantity(new ca.teamdman.sfml.ast.Number(quantity), NO_EXPAND),
                new ResourceQuantity(new ca.teamdman.sfml.ast.Number(retention), NO_EXPAND));
        var tracker = new ResourceLimit(ids, limit, With.ALWAYS_TRUE).createOutputTracker(ResourceIdSet.EMPTY);
        tracker.updateRetentionObservation(SFMResourceTypes.FORGE_ENERGY.get(), handler.getEnergyStored());
        return new LimitedOutputSlot<>(new Label("destination"), pos, null, 0, handler, tracker,
                handler.getEnergyStored(), SFMResourceTypes.FORGE_ENERGY.get());
    }

    /** Reduced observed behavior; simulation and execution are deliberately consistent. */
    private static class QuantizedReceiver extends EnergyStorage {
        private final boolean reject;
        QuantizedReceiver(int capacity, int initial, boolean reject) {
            super(capacity);
            this.energy = initial;
            this.reject = reject;
        }
        @Override public int receiveEnergy(int amount, boolean simulate) {
            int available = reject ? 0 : Math.min(amount, capacity - energy);
            return super.receiveEnergy(available - available % 2, simulate);
        }
    }
}
