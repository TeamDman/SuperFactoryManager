package ca.teamdman.sfm.gametest.tests.compat.justdirethings;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
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
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

/** Manual recording controls; the original frozen-evidence fixture remains unchanged. */
@SFMGameTest
@MCVersionDependentBehaviour
public class Issue610VisualControlsGameTest extends SFMGameTestDefinition {
    // A recording can wait for 24 hours of server ticks; automatic actions have short deadlines.
    private static final int RECORDING_TICKS = 20 * 60 * 60 * 24;
{% if features.quantity_sensitive_extraction_progress %}
    private static final boolean EXPECT_FIXED = true;
{% else %}
    private static final boolean EXPECT_FIXED = false;
{% endif %}
    private static final BlockPos MANAGER = new BlockPos(2, 1, 3);
    private static final BlockPos SOURCE = new BlockPos(2, 3, 3);
    private static final BlockPos DROPPER = new BlockPos(1, 2, 3);
    private static final BlockPos HEALTHY = new BlockPos(3, 2, 3);
    private static final BlockPos RUN_BUTTON = new BlockPos(2, 1, 2);
    private static final BlockPos DRAIN_BUTTON = new BlockPos(7, 1, 2);
    private static final BlockPos MANUAL_LEVER = new BlockPos(9, 1, 2);
    private static final BlockPos TOKENS = new BlockPos(5, 2, 2);
    private static final BlockPos COUNTER = new BlockPos(5, 2, 4);
    private static final BlockPos STATUS = new BlockPos(5, 1, 1);
    private static final BlockPos FIRST_LAMP = new BlockPos(7, 2, 4);
    private static final BlockPos SECOND_LAMP = new BlockPos(9, 2, 4);
    private static final String IDLE_PROGRAM = "EVERY REDSTONE PULSE DO\nEND";
    private static final String PROGRAM = """
            EVERY REDSTONE PULSE DO
                INPUT fe:: FROM PowerSource TOP SIDE
                OUTPUT fe:: TO Dropper, Healthy
                FORGET
                INPUT 1 minecraft:iron_shovel FROM RunTokens
                OUTPUT TO RunCounter
                FORGET
            END
            """;

    @Override public String template() { return "11x5x7"; }
    @Override public String testName() { return "issue_610_visual_controls"; }
    @Override public int maxTicks() { return RECORDING_TICKS; }

    @Override public void run(SFMGameTestHelper helper) {
        helper.setBlock(MANAGER, SFMBlocks.MANAGER.get());
        for (BlockPos pos : List.of(new BlockPos(2, 2, 3), new BlockPos(2, 2, 4), new BlockPos(3, 2, 4),
                new BlockPos(4, 2, 4), new BlockPos(4, 2, 3), new BlockPos(5, 2, 3))) {
            helper.setBlock(pos, SFMBlocks.CABLE.get());
        }
        helper.setBlock(SOURCE, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
        helper.setBlock(DROPPER, Registration.DropperT2.get());
        helper.setBlock(HEALTHY, Registration.DropperT2.get());
        helper.setBlock(TOKENS, Blocks.CHEST);
        helper.setBlock(COUNTER, Blocks.CHEST);
        helper.setBlock(RUN_BUTTON, Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH));
        helper.setBlock(DRAIN_BUTTON.below(), Blocks.STONE);
        helper.setBlock(DRAIN_BUTTON, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR));
        helper.setBlock(MANUAL_LEVER.below(), Blocks.STONE);
        helper.setBlock(MANUAL_LEVER, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        for (BlockPos lamp : List.of(FIRST_LAMP, SECOND_LAMP)) {
            helper.setBlock(lamp.below(), Blocks.STONE);
            helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        }
        sign(helper, new BlockPos(1, 1, 1), "RUN MANAGER", "One pulse", "Run 1 > drain", "then run 2");
        sign(helper, new BlockPos(6, 1, 1), "DRAIN SECOND", "After run 1", "Healthy only", "Then run again");
        sign(helper, new BlockPos(9, 1, 1), "MANUAL MODE", "Flick within 3s", "Otherwise auto", "Choice is latched");
        sign(helper, new BlockPos(7, 1, 5), "RUNS >= 1", "Off/off: none", "On/off: one", "On/on: two+");
        sign(helper, new BlockPos(9, 1, 5), "RUNS >= 2", "Counted tokens", "Not button taps", "Reset: rerun test");
        sign(helper, STATUS, "PREPARING", "Do not press yet", "", "");
        helper.runAfterDelay(2, () -> new Session(helper).configure());
    }

    private static void sign(SFMGameTestHelper helper, BlockPos pos, String... lines) {
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, Blocks.OAK_SIGN);
        text(helper, pos, lines);
    }

    private static void text(SFMGameTestHelper helper, BlockPos pos, String... lines) {
        Component[] components = java.util.Arrays.stream(lines).map(Component::literal).toArray(Component[]::new);
        helper.setSignText(pos, components);
    }

    private static final class Session {
        private final SFMGameTestHelper helper;
        private final ManagerBlockEntity manager;
        private final TileEntityEnergyCube source;
        private final DropperT2BE dropper, healthy;
        private final ChestBlockEntity tokens, counter;
        private Issue610VisualControls controls;
        private long initialJoules, previousJoules, selectionStarted, pendingSince;
        private int runs, pendingRun;
        private boolean drained, runPowered, drainPowered, finished;
        private Throwable failure;
        private String previousStatus = "";

        Session(SFMGameTestHelper helper) {
            this.helper = helper;
            manager = helper.getBlockEntity(MANAGER, ManagerBlockEntity.class);
            source = helper.getBlockEntity(SOURCE, TileEntityEnergyCube.class);
            dropper = helper.getBlockEntity(DROPPER, DropperT2BE.class);
            healthy = helper.getBlockEntity(HEALTHY, DropperT2BE.class);
            tokens = helper.getBlockEntity(TOKENS, ChestBlockEntity.class);
            counter = helper.getBlockEntity(COUNTER, ChestBlockEntity.class);
        }

        void configure() {
            var config = source.getConfig().getConfig(TransmissionType.ENERGY);
            config.setEjecting(false);
            if (config.setDataType(DataType.OUTPUT, RelativeSide.TOP)) {
                source.getConfig().sideChanged(TransmissionType.ENERGY, RelativeSide.TOP);
            }
            for (DropperT2BE machine : List.of(dropper, healthy)) {
                machine.getRedstoneControlData().redstoneMode = MiscHelpers.RedstoneMode.HIGH;
                machine.getMachineHandler().setStackInSlot(0, ItemStack.EMPTY);
            }
            require(dropper.getMaxEnergy() == 100000 && dropper.getStandardEnergyCost() == 25,
                    "Pinned JDT fixture requires 100000 FE capacity and a real 25 FE operation");
            source.setEnergy(0, source.getMaxEnergy(0));
            dropper.setEnergyStored(100000);
            healthy.setEnergyStored(0);
            dropper.spawnItem(helper.getLevel(), new ItemStack(Items.COBBLESTONE), 0,
                    Direction.UP, Vec3.atCenterOf(helper.absolutePos(DROPPER).above()));
            require(dropper.getEnergyStored() == 99975, "Real first-dropper operation must leave 99975 FE");
            initialJoules = previousJoules = source.getEnergy(0);
            var sourceFe = helper.getEnergyStorage(SOURCE, Direction.UP);
            require(sourceFe.extractEnergy(1, true) == 0 && sourceFe.extractEnergy(25, true) == 24
                    && sourceFe.extractEnergy(100000, true) == 100000, "Pinned Mekanism transfer quantum/supply changed");
            require(source.getEnergy(0) == initialJoules, "Simulation changed source joules");
            tokens.clearContent();
            counter.clearContent();
            for (int slot = 0; slot < 3; slot++) tokens.setItem(slot, new ItemStack(Items.IRON_SHOVEL));
            manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
            LabelPositionHolder.empty()
                    .add("PowerSource", helper.absolutePos(SOURCE))
                    .add("Dropper", helper.absolutePos(DROPPER))
                    .add("Healthy", helper.absolutePos(HEALTHY))
                    .add("RunTokens", helper.absolutePos(TOKENS))
                    .add("RunCounter", helper.absolutePos(COUNTER))
                    .save(Objects.requireNonNull(manager.getDisk()));
            manager.setProgram(PROGRAM);
            manager.clearRedstonePulseQueue();
            helper.assertManagerRunning(manager);
            selectionStarted = helper.getLevel().getGameTime();
            controls = new Issue610VisualControls(selectionStarted);
            helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo test) {}
                @Override public void testPassed(GameTestInfo test, GameTestRunner runner) {}
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {}
                @Override public void testFailed(GameTestInfo test, GameTestRunner runner) {
                    finished = true;
                    try {
                        manager.setProgram(IDLE_PROGRAM);
                        manager.clearRedstonePulseQueue();
                        text(helper, STATUS, "FIXTURE FAILED", "Known runs: " + runs, "See server log", "Reset: rerun test");
                    } catch (Throwable cleanupError) {
                        SFM.LOGGER.error("ISSUE610_CONTROLS failure cleanup", cleanupError);
                    }
                }
            });
            SFM.LOGGER.info("ISSUE610_CONTROLS ready expected={} initialDropperFE=99975 laterFE=0 sourceJ={} selectionTicks=60 gapTicks=40 program={}",
                    EXPECT_FIXED ? "fixed_progress" : "baseline_starvation", initialJoules, PROGRAM);
            helper.succeedWhen(() -> {
                poll();
                if (failure != null) {
                    helper.testInfo.fail(failure);
                    helper.fail("Issue610 controls failed: " + failure.getMessage());
                }
                helper.assertTrue(finished, "Waiting for selection or physical controls; manual recording limit is 24 hours");
            });
        }

        private void poll() {
            if (finished) return;
            try {
                long tick = helper.getLevel().getGameTime();
                boolean currentRun = helper.getBlockState(RUN_BUTTON).getValue(ButtonBlock.POWERED);
                boolean currentDrain = helper.getBlockState(DRAIN_BUTTON).getValue(ButtonBlock.POWERED);
                boolean manual = helper.getBlockState(MANUAL_LEVER).getValue(LeverBlock.POWERED);
                if (currentRun && !runPowered) {
                    require(pendingRun == 0, "A manager pulse is still awaiting its execution witness");
                    pendingRun = runs + 1;
                    pendingSince = tick;
                }
                int actualRuns = count(counter);
                require(actualRuns + count(tokens) == 3, "Execution marker items were lost, inserted or removed");
                require(manager.getUnprocessedRedstonePulseCount() <= 1, "Multiple pulses queued: expected one physical press at a time");
                if (actualRuns != runs) {
                    lamps(actualRuns);
                    require(actualRuns == runs + 1 && actualRuns <= 2, "Expected exactly one new completed body; actual marker count=" + actualRuns);
                    observePass(actualRuns);
                    runs = actualRuns;
                    pendingRun = 0;
                }
                if (currentDrain && !drainPowered && runs == 1 && !drained) {
                    // Both modes reach this branch only by powering the real drain button.
                    require(dropper.getEnergyStored() == 99999 && healthy.getEnergyStored() == 100000,
                            "Drain requires the completed first pass with an idle 99999-FE first dropper");
                    long before = source.getEnergy(0);
                    healthy.setEnergyStored(0);
                    drained = true;
                    require(source.getEnergy(0) == before && dropper.getEnergyStored() == 99999,
                            "Drain changed something other than the second dropper");
                    SFM.LOGGER.info("ISSUE610_CONTROLS drain runCount={} firstFE=99999 laterFE=0 sourceJ={} simulatedInterveningWork=true", runs, before);
                }
                var action = controls.tick(tick, manual, currentRun, currentDrain, runs, drained);
                require(controls.mode() != Issue610VisualControls.Mode.SELECTING || runs == 0,
                        "Select manual with the lever before pressing the manager during the three-second window");
                if (pendingRun != 0 && tick - pendingSince >= Issue610VisualControls.ACTION_TIMEOUT_TICKS) {
                    throw new IllegalStateException("Manager press produced no completed body within 100 ticks; inspect program/marker inventory");
                }
                require(dropper.getEnergyStored() == (runs == 0 ? 99975 : 99999), "Idle first dropper changed between passes");
                require(healthy.getEnergyStored() == (runs == 0 || drained ? 0 : 100000)
                        || runs == 2 && EXPECT_FIXED && healthy.getEnergyStored() == 100000,
                        "Second dropper changed without the drain button or manager pass");
                require(source.getEnergy(0) == previousJoules, "Source changed outside a completed manager pass");
                runPowered = currentRun;
                drainPowered = currentDrain;
                if (action == Issue610VisualControls.Action.PRESS_MANAGER) helper.pressButton(RUN_BUTTON);
                if (action == Issue610VisualControls.Action.PRESS_DRAIN) helper.pressButton(DRAIN_BUTTON);
                status(tick);
                if (runs == 2) {
                    manager.setProgram(IDLE_PROGRAM);
                    manager.clearRedstonePulseQueue();
                    finished = true;
                    text(helper, STATUS, EXPECT_FIXED ? "FIXED: REFILLED" : "BASELINE: EMPTY", "Completed runs: 2", "First: 99999 FE", "Reset: rerun test");
                    SFM.LOGGER.info("ISSUE610_CONTROLS complete mode={} actualRuns=2 firstFE={} laterFE={} sourceJ={} expected={}",
                            controls.mode(), dropper.getEnergyStored(), healthy.getEnergyStored(), source.getEnergy(0), EXPECT_FIXED);
                }
            } catch (Throwable error) {
                failure = error;
                finished = true;
                helper.testInfo.fail(error);
                try {
                    manager.setProgram(IDLE_PROGRAM);
                    manager.clearRedstonePulseQueue();
                    text(helper, STATUS, "FIXTURE FAILED", "Known runs: " + runs, "See server log", "Reset: rerun test");
                } catch (Throwable cleanupError) {
                    error.addSuppressed(cleanupError);
                }
                SFM.LOGGER.error("ISSUE610_CONTROLS failed", error);
            }
        }

        private void observePass(int pass) {
            require(pass == 1 || drained, "Second manager pass requires the explicit drain-button action first");
            int first = dropper.getEnergyStored(), second = healthy.getEnergyStored();
            int expectedSecond = pass == 1 || EXPECT_FIXED ? 100000 : 0;
            require(first == 99999 && second == expectedSecond,
                    "Pass " + pass + " expected first=99999/later=" + expectedSecond + " FE, got " + first + "/" + second);
            long joules = source.getEnergy(0);
            long moved = first - (pass == 1 ? 99975 : 99999) + second;
            require((previousJoules - joules) * 2 == moved * 5, "Raw FE/joule conservation failed on pass " + pass);
            previousJoules = joules;
            require(helper.getEnergyStorage(SOURCE, Direction.UP).extractEnergy(100000, true) == 100000,
                    "Source must still have sufficient supply; this fixture never refills it between passes");
            SFM.LOGGER.info("ISSUE610_CONTROLS pass={} actualRuns={} firstFE={} laterFE={} sourceJ={} movedFE={} initialJ={}",
                    pass, pass, first, second, joules, moved, initialJoules);
        }

        private static int count(ChestBlockEntity chest) {
            int count = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                require(stack.isEmpty() || stack.is(Items.IRON_SHOVEL), "Unexpected item in execution-marker inventory");
                count += stack.getCount();
            }
            return count;
        }

        private void lamps(int actualRuns) {
            // Power normal lamps from the counted outputs, far from manager/lever/button wiring.
            helper.setBlock(FIRST_LAMP.below(), actualRuns >= 1 ? Blocks.REDSTONE_BLOCK : Blocks.STONE);
            helper.setBlock(SECOND_LAMP.below(), actualRuns >= 2 ? Blocks.REDSTONE_BLOCK : Blocks.STONE);
        }

        private void status(long tick) {
            String title = controls.mode().toString();
            String detail = controls.mode() == Issue610VisualControls.Mode.SELECTING
                    ? "Lever: " + Math.max(0, 3 - (tick - selectionStarted) / 20) + "s left" : controls.phase().toString();
            String next = title + detail + runs + drained;
            if (!next.equals(previousStatus)) {
                text(helper, STATUS, title, detail, "Completed runs: " + runs, drained ? "Second drained" : "Drain after run 1");
                previousStatus = next;
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
