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
 * Real-block progress regression for issue 610, runnable on both unfixed and fixed projections.
 * The adapter's one-FE quantum remains observable; later consumers must continue receiving energy.
 * JDT is a test-only dependency on 1.21.1; do not propagate this fixture to 1.19.2.
 */
@SFMGameTestGenerator
@MCVersionDependentBehaviour
public class Issue610VisualFixtureGameTestGenerator extends SFMGameTestGeneratorBase {
    public static volatile State state;
    public static final class State {
        public Runnable start;
        public BlockPos sourcePos, dropperPos, healthyPos;
        public long fullJoules, finalJoules;
        public int finalDropper, finalHealthy;
        public volatile boolean armed, finished;
        public volatile Throwable failure;
    }
    private enum Scenario {
        SINGLE_LABEL, DROPPER_FIRST, HEALTHY_FIRST, FULL_DROPPER, QUANTITY_1000, RETAIN_1000, EACH_SIDE
    }

    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> tests) {
        tests.accept(new Fixture(Scenario.DROPPER_FIRST));
    }

    private static class Fixture extends SFMGameTestDefinition {
        private final Scenario scenario;

        Fixture(Scenario scenario) { this.scenario = scenario; }

        @Override public String template() { return "3x4x3"; }
        @Override public int maxTicks() { return 200; }
        @Override public String testName() {
            return "issue_610_visual_fixture";
        }

        @Override
        public void run(SFMGameTestHelper helper) {
            state = null;
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
            var observation = new State();
            observation.fullJoules = fullJoules;
            observation.sourcePos = sourcePos; observation.dropperPos = dropperPos; observation.healthyPos = healthyPos;
            manager.addProgramHooks(new IProgramHooks() {
                private int passes;
                @Override public void onProgramDidSomething(Duration elapsed) {
                    if (!observation.armed || passes >= 2 || observation.finished) return;
                    try {
                        int pass = ++passes;
                        int d = dropperFe.getEnergyStored(), h = healthyFe.getEnergyStored();
                        long j = source.getEnergy(0);
                        long moved = d - (pass == 1 ? initialDropper : 99999) + h;
                        if ((fullJoules - j) * 2 != moved * 5) throw new IllegalStateException("Visual conservation failed");
                        if (d != 99999 || (pass == 1 && h != 100000) || (pass == 2 && h != 0 && h != 100000))
                            throw new IllegalStateException("Unexpected visual characterization state");
                        SFM.LOGGER.info("ISSUE610_VISUAL pass={} dropperFE={} laterFE={} sourceJ={} fullJ={}",pass,d,h,j,fullJoules);
                        if (pass == 1) { source.setEnergy(0,fullJoules); healthy.setEnergyStored(0); }
                        else {
                            observation.finalDropper=d; observation.finalHealthy=h; observation.finalJoules=j;
                            manager.setProgram("EVERY 200000 TICKS DO\nINPUT fe:: FROM PowerSource TOP SIDE\nOUTPUT fe:: TO Dropper, Healthy\nEND");
                            observation.finished=true;
                        }
                    } catch (Throwable t) { observation.failure=t; observation.finished=true; }
                }
            });
            String input = scenario == Scenario.EACH_SIDE
                    ? "INPUT fe:: FROM PowerSource EACH SIDE" : "INPUT fe:: FROM PowerSource TOP SIDE";
            String program = "EVERY TICK DO\n" + input + "\n" + output() + "\nEND";
            observation.start = () -> { observation.armed=true; manager.setProgram(program); };
            manager.setProgram("EVERY TICK DO\nEND");
            state = observation;
            SFM.LOGGER.info("ISSUE610_VISUAL before dropperFE={} laterFE=0 sourceJ={} program={}",initialDropper,fullJoules,program);
            helper.succeed();
        }

        private String output() {
            return switch (scenario) {
                case SINGLE_LABEL -> "OUTPUT fe:: TO Machines";
                case EACH_SIDE -> "OUTPUT fe:: TO EACH Dropper, Healthy";
                case HEALTHY_FIRST -> "OUTPUT fe:: TO Healthy, Dropper";
                case QUANTITY_1000 -> "OUTPUT 1000 fe:: TO EACH Dropper, Healthy";
                case RETAIN_1000 -> "OUTPUT RETAIN 1000 fe:: TO EACH Dropper, Healthy";
                default -> "OUTPUT fe:: TO Dropper, Healthy";
            };
        }

        private int expectedDropper() {
            return switch (scenario) {
                case FULL_DROPPER -> 100000;
                case RETAIN_1000 -> 99975;
                default -> 99999;
            };
        }

        private int expectedHealthy(int pass) {
            return scenario == Scenario.QUANTITY_1000 || scenario == Scenario.RETAIN_1000 ? 1000 : 100000;
        }
    }
}
