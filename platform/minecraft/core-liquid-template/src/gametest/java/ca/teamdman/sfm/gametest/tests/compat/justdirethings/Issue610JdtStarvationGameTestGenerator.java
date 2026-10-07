package ca.teamdman.sfm.gametest.tests.compat.justdirethings;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.IProgramHooks;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestGenerator;
import ca.teamdman.sfm.gametest.SFMGameTestGeneratorBase;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import com.direwolf20.justdirethings.common.blockentities.DropperT2BE;
import com.direwolf20.justdirethings.setup.Registration;
import com.direwolf20.justdirethings.util.MiscHelpers;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Real-block characterization of issue 610, not an assertion that starvation is correct.
 * These expectations must become forward-progress regressions when the production bug is fixed.
 * JDT is a test-only dependency on 1.21.1; do not propagate this fixture to 1.19.2.
 */
@SFMGameTestGenerator
@MCVersionDependentBehaviour
public class Issue610JdtStarvationGameTestGenerator extends SFMGameTestGeneratorBase {
    private enum Scenario {
        SINGLE_LABEL, DROPPER_FIRST, HEALTHY_FIRST, FULL_DROPPER, QUANTITY_1000, RETAIN_1000
    }

    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> tests) {
        for (Scenario scenario : Scenario.values()) tests.accept(new Fixture(scenario));
    }

    private static class Fixture extends SFMGameTestDefinition {
        private final Scenario scenario;

        Fixture(Scenario scenario) { this.scenario = scenario; }

        @Override public String template() { return "3x4x3"; }
        @Override public int maxTicks() { return 200; }
        @Override public String testName() {
            return "issue_610_jdt_" + scenario.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public void run(SFMGameTestHelper helper) {
            BlockPos managerPos = new BlockPos(1, 1, 1);
            BlockPos cablePos = new BlockPos(1, 2, 1);
            BlockPos sourcePos = new BlockPos(1, 3, 1);
            List<BlockPos> destinations = List.of(new BlockPos(0, 2, 1), new BlockPos(2, 2, 1));
            var labels = LabelPositionHolder.empty().add("PowerSource", helper.absolutePos(sourcePos));
            for (BlockPos pos : destinations) labels.add("Machines", helper.absolutePos(pos));
            helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
            var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
            manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
            labels.save(Objects.requireNonNull(manager.getDisk()));

            // The report uses a single label with unspecified position ordering. Assign the
            // troublesome machine to the first position in the actual label iterator, rather
            // than relying on insertion order, structure origin, or hash implementation luck.
            // Saving copies the hash set and can change its capacity/iteration order.
            BlockPos first = LabelPositionHolder.from(manager.getDisk())
                    .getPositions("Machines").blockPosIterator().next().immutable();
            BlockPos dropperPos = destinations.stream()
                    .filter(pos -> helper.absolutePos(pos).equals(first)).findFirst().orElseThrow();
            BlockPos healthyPos = destinations.stream().filter(pos -> !pos.equals(dropperPos))
                    .findFirst().orElseThrow();
            labels.add("Dropper", helper.absolutePos(dropperPos));
            labels.add("Healthy", helper.absolutePos(healthyPos));
            helper.setBlock(cablePos, SFMBlocks.CABLE.get());
            helper.setBlock(sourcePos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
            helper.setBlock(dropperPos, Registration.DropperT2.get());
            helper.setBlock(healthyPos, Registration.DropperT2.get());

            // Let placed block entities enter the level before changing capability sides.
            helper.runAfterDelay(2, () -> configure(helper, managerPos, sourcePos, dropperPos, healthyPos, labels));
        }

        private void configure(SFMGameTestHelper helper, BlockPos managerPos, BlockPos sourcePos,
                               BlockPos dropperPos, BlockPos healthyPos, LabelPositionHolder labels) {
            var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
            var source = helper.getBlockEntity(sourcePos, TileEntityEnergyCube.class);
            var dropper = helper.getBlockEntity(dropperPos, DropperT2BE.class);
            var healthy = helper.getBlockEntity(healthyPos, DropperT2BE.class);
            var config = source.getConfig().getConfig(TransmissionType.ENERGY);
            config.setEjecting(false);
            if (config.setDataType(DataType.OUTPUT, RelativeSide.TOP)) {
                source.getConfig().sideChanged(TransmissionType.ENERGY, RelativeSide.TOP);
            }
            for (DropperT2BE machine : List.of(dropper, healthy)) {
                machine.getRedstoneControlData().redstoneMode = MiscHelpers.RedstoneMode.HIGH;
                machine.getMachineHandler().setStackInSlot(0, ItemStack.EMPTY);
            }
            helper.assertTrue(dropper.getMaxEnergy() == 100000, "Fixture requires the default JDT capacity");
            helper.assertTrue(dropper.getStandardEnergyCost() == 25, "Expected a 25-FE drop operation");
            source.setEnergy(0, source.getMaxEnergy(0));
            dropper.setEnergyStored(dropper.getMaxEnergy());
            healthy.setEnergyStored(0);
            if (scenario != Scenario.FULL_DROPPER) {
                // Execute the real operation that spends 25 FE, then leave the empty machine idle.
                dropper.spawnItem(helper.getLevel(), new ItemStack(Items.COBBLESTONE), 0,
                        Direction.UP, Vec3.atCenterOf(helper.absolutePos(dropperPos).above()));
            }
            int initialDropper = scenario == Scenario.FULL_DROPPER ? 100000 : 99975;
            helper.assertTrue(dropper.getEnergyStored() == initialDropper, "Real drop operation used unexpected FE");
            var sourceFe = helper.getEnergyStorage(sourcePos, Direction.UP);
            var dropperFe = helper.getEnergyStorage(dropperPos, null);
            var healthyFe = helper.getEnergyStorage(healthyPos, null);
            long fullJoules = source.getEnergy(0);
            helper.assertTrue(sourceFe.extractEnergy(1, true) == 0
                              && sourceFe.extractEnergy(25, true) == 24
                              && sourceFe.extractEnergy(1000, true) == 1000,
                    "Pinned Mekanism/default 2.5 J per FE must exhibit the measured transfer quantum");
            helper.assertTrue(source.getEnergy(0) == fullJoules, "Source simulation mutated energy");
            helper.assertTrue(dropperFe.getEnergyStored() == initialDropper
                              && healthyFe.receiveEnergy(100000, true) == 100000
                              && healthy.getEnergyStored() == 0,
                    "World capability discovery or non-mutating JDT insertion simulation failed");

            labels.save(Objects.requireNonNull(manager.getDisk()));
            if (scenario == Scenario.SINGLE_LABEL) {
                helper.assertTrue(LabelPositionHolder.from(manager.getDisk()).getPositions("Machines")
                                          .blockPosIterator().next().equals(helper.absolutePos(dropperPos)),
                        "Persisted single-label order changed before execution; fixture must put the dropper first");
            }
            manager.addProgramHooks(new IProgramHooks() {
                private int passes;

                @Override public void onProgramDidSomething(Duration elapsed) {
                    if (passes >= 5) return;
                    int pass = ++passes;
                    int dropperAfter = dropperFe.getEnergyStored();
                    int healthyAfter = healthyFe.getEnergyStored();
                    long sourceAfter = source.getEnergy(0);
                    int previousDropper = pass == 1 ? initialDropper : expectedDropper();
                    // Replenishment and synthetic load happen between complete real SFML passes.
                    // No capability or transfer code is substituted. The culprit stays idle.
                    source.setEnergy(0, fullJoules);
                    healthy.setEnergyStored(0);
                    helper.runAfterDelay(0, () -> {
                        helper.assertManagerRunning(manager);
                        helper.assertTrue(dropperAfter == expectedDropper(),
                                testName() + " pass " + pass + ": dropper FE=" + dropperAfter);
                        int expectedHealthy = expectedHealthy(pass);
                        helper.assertTrue(healthyAfter == expectedHealthy,
                                testName() + " pass " + pass + ": later machine expected "
                                + expectedHealthy + " FE, got " + healthyAfter);
                        long moved = dropperAfter - previousDropper + healthyAfter;
                        helper.assertTrue((fullJoules - sourceAfter) * 2 == moved * 5,
                                "Energy loss/duplication is distinct from starvation: moved=" + moved);
                        if (pass > 1 && starves()) {
                            helper.assertTrue(sourceAfter == fullJoules,
                                    "Starvation must occur despite a completely full source");
                        }
                        SFM.LOGGER.info("{} pass {}: dropper={} FE, later={} FE, source={}/{} J",
                                testName(), pass, dropperAfter, healthyAfter, sourceAfter, fullJoules);
                        if (pass == 5) {
                            // Remove synthetic load after assertions so the world remains inspectable.
                            healthy.setEnergyStored(healthyAfter);
                            helper.succeed();
                        }
                    });
                }
            });
            manager.setProgram("EVERY TICK DO\nINPUT fe:: FROM PowerSource TOP SIDE\n" + output() + "\nEND");
        }

        private String output() {
            return switch (scenario) {
                case SINGLE_LABEL -> "OUTPUT fe:: TO Machines";
                case HEALTHY_FIRST -> "OUTPUT fe:: TO Healthy, Dropper";
                case QUANTITY_1000 -> "OUTPUT 1000 fe:: TO EACH Dropper, Healthy";
                case RETAIN_1000 -> "OUTPUT RETAIN 1000 fe:: TO EACH Dropper, Healthy";
                default -> "OUTPUT fe:: TO Dropper, Healthy";
            };
        }

        private boolean starves() {
            return scenario == Scenario.SINGLE_LABEL || scenario == Scenario.DROPPER_FIRST
                   || scenario == Scenario.QUANTITY_1000;
        }

        private int expectedDropper() {
            return switch (scenario) {
                case FULL_DROPPER -> 100000;
                case RETAIN_1000 -> 99975;
                default -> 99999;
            };
        }

        private int expectedHealthy(int pass) {
            if (pass > 1 && starves()) return 0;
            return scenario == Scenario.QUANTITY_1000 || scenario == Scenario.RETAIN_1000 ? 1000 : 100000;
        }
    }
}
