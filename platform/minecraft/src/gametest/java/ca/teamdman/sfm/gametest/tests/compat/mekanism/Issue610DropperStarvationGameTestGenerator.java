package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static ca.teamdman.sfml.ast.ResourceQuantity.IdExpansionBehaviour.NO_EXPAND;

/**
 * Current-bug characterization, not a fix or a full Oceanblock runtime test.
 * Uses the observed Mekanism 1.21.1 extraction quantum and JDT's ordinary FE
 * storage/25-FE drop cost with real SFM label ordering, trackers and moveTo.
 */
@SFMGameTestGenerator
public class Issue610DropperStarvationGameTestGenerator extends SFMGameTestGeneratorBase {
    private record Scenario(String name, boolean quantizedSource, boolean healthyFirst,
                            long quantity, long retention, boolean starves) {}

    @Override public void generateTests(Consumer<SFMGameTestDefinition> tests) {
        long max = Long.MAX_VALUE;
        for (var scenario : List.of(
                new Scenario("dropper_first", true, false, max, max, true),
                new Scenario("healthy_label_first", true, true, max, max, false),
                new Scenario("ordinary_fe_source", false, false, max, max, false),
                new Scenario("quantity_1000_still_starves", true, false, 1000, max, true),
                new Scenario("retain_1000_skips_near_full", true, false, max, 1000, false)
        )) tests.accept(new StarvationTest(scenario));
    }

    private static class StarvationTest extends SFMGameTestDefinition {
        private final Scenario scenario;
        StarvationTest(Scenario scenario) { this.scenario = scenario; }
        @Override public String template() { return "3x2x1"; }
        @Override public String testName() { return "issue_610_dropper_" + scenario.name(); }

        @Override public void run(SFMGameTestHelper helper) {
            var managerPos = new BlockPos(1, 2, 0);
            helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
            var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
            manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
            manager.setProgram("EVERY 20 TICKS DO\nEND");
            helper.assertManagerRunning(manager);
            var context = new ProgramContext(Objects.requireNonNull(manager.getProgram()), manager, new ExecuteProgramBehaviour());
            var type = SFMResourceTypes.FORGE_ENERGY.get();
            var ids = new ResourceIdSet(List.of(new ResourceIdentifier<>("sfm", "forge_energy", ".*", ".*")));
            var source = new EnergyStorage(1000000) {
                @Override public int extractEnergy(int request, boolean simulate) {
                    return super.extractEnergy(scenario.quantizedSource() ? request - request % 2 : request, simulate);
                }
            };
            var dropper = new EnergyStorage(100000);
            dropper.receiveEnergy(100000, false);
            dropper.extractEnergy(25, false); // One real JDT drop's cost, then the dropper is idle.
            var dropperPos = helper.absolutePos(managerPos).offset(0, 0, 1);
            var labels = LabelPositionHolder.empty().add("HungryMachines", dropperPos);
            Map<BlockPos, EnergyStorage> handlers = new HashMap<>();
            handlers.put(dropperPos, dropper);
            for (int i = 0; i < 9; i++) {
                var pos = dropperPos.offset(i + 1, 0, 0);
                handlers.put(pos, new EnergyStorage(1000));
                labels.add("Machines", pos);
            }
            var labelOrder = scenario.healthyFirst()
                    ? List.of(new Label("Machines"), new Label("HungryMachines"))
                    : List.of(new Label("HungryMachines"), new Label("Machines"));
            var order = RoundRobin.disabled().getPositionsForLabels(labelOrder, labels);
            helper.assertTrue(order.size() == 10, "Expected the dropper and nine healthy destinations");

            // Each pass is a fresh INPUT evaluation. The source refills between passes,
            // and healthy machines consume their previous allocation. No dropper use.
            for (int pass = 0; pass < 5; pass++) {
                source.receiveEnergy(1000000, false);
                for (var entry : handlers.entrySet()) {
                    if (entry.getValue() != dropper) entry.getValue().extractEnergy(1000, false);
                }
                int dropperBefore = dropper.getEnergyStored();
                var input = new LimitedInputSlot<Integer, Class<Integer>, IEnergyStorage>(
                        new Label("PowerSource"), helper.absolutePos(managerPos), null, 0, source,
                        new ResourceLimit(ids, Limit.MAX_QUANTITY_NO_RETENTION, With.ALWAYS_TRUE).createInputTracker(ResourceIdSet.EMPTY),
                        source.getEnergyStored(), type);
                for (var target : order) {
                    if (input.isDone()) break;
                    var handler = handlers.get(target.getSecond());
                    var limit = new Limit(quantity(scenario.quantity()), quantity(scenario.retention()));
                    var tracker = new ResourceLimit(ids, limit, With.ALWAYS_TRUE).createOutputTracker(ResourceIdSet.EMPTY);
                    tracker.updateRetentionObservation(type, handler.getEnergyStored());
                    var output = new LimitedOutputSlot<Integer, Class<Integer>, IEnergyStorage>(
                            target.getFirst(), target.getSecond(), null, 0, handler, tracker, handler.getEnergyStored(), type);
                    if (!output.isDone()) OutputStatement.moveTo(context, input, output);
                }
                int healthyTotal = handlers.values().stream().filter(it -> it != dropper).mapToInt(EnergyStorage::getEnergyStored).sum();
                boolean starvedThisPass = pass > 0 && scenario.starves();
                helper.assertTrue(healthyTotal == (starvedThisPass ? 0 : 9000),
                        "Unexpected healthy-machine total on pass=" + pass + ": " + healthyTotal);
                int expectedDropper = scenario.retention() == 1000 ? 99975 : scenario.quantizedSource() ? 99999 : 100000;
                helper.assertTrue(dropper.getEnergyStored() == expectedDropper, "Unexpected dropper energy");
                helper.assertTrue(source.getEnergyStored() + healthyTotal + dropper.getEnergyStored() - dropperBefore == 1000000,
                        "This starvation case must conserve all energy, unlike insertion loss");
                if (starvedThisPass) {
                    helper.assertTrue(source.getEnergyStored() == 1000000 && input.isDone(),
                            "The still-full source must be incorrectly marked done");
                    helper.assertTrue(source.extractEnergy(1000, true) == 1000, "The full source can still supply larger requests");
                }
            }
            helper.succeed();
        }
    }

    private static ResourceQuantity quantity(long amount) {
        return new ResourceQuantity(new ca.teamdman.sfml.ast.Number(amount), NO_EXPAND);
    }
}
