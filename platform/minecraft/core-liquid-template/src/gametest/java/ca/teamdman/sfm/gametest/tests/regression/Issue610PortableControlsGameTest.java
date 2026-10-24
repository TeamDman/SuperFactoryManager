package ca.teamdman.sfm.gametest.tests.regression;

import ca.teamdman.sfm.common.blockentity.TestBarrelBlockEntity;
import ca.teamdman.sfm.common.capability.Issue610FixtureEnergyProvider;
import ca.teamdman.sfm.common.capability.Issue610FixtureEnergyProvider.Storage;
import ca.teamdman.sfm.gametest.tests.compat.justdirethings.Issue610VisualControls;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.List;
import java.util.Objects;

/** Deterministic FE regression through the real manager; not Mekanism/JDT integration proof. */
@SFMGameTest
@MCVersionDependentBehaviour
public class Issue610PortableControlsGameTest extends SFMGameTestDefinition {
    // A recording can wait for 24 hours of server ticks; automatic actions have short deadlines.
    private static final int RECORDING_TICKS = 20 * 60 * 60 * 24;
{% if features.quantity_sensitive_extraction_progress %}
    private static final boolean EXPECT_FIXED = true;
{% else %}
    private static final boolean EXPECT_FIXED = false;
{% endif %}
    private static final BlockPos MANAGER = new BlockPos(2, 1, 3);
    private static final BlockPos SOURCE = new BlockPos(2, 3, 3);
    private static final BlockPos FIRST = new BlockPos(1, 2, 3);
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
                OUTPUT fe:: TO First, Healthy
                FORGET
                INPUT 1 minecraft:iron_shovel FROM RunTokens
                OUTPUT TO RunCounter
                FORGET
            END
            """;

    @Override public String template() { return "11x5x7"; }
    @Override public String testName() { return "issue_610_portable_controls"; }
    @Override public int maxTicks() { return RECORDING_TICKS; }

    @Override public void run(SFMGameTestHelper helper) {
        helper.setBlock(MANAGER, SFMBlocks.MANAGER.get());
        for (BlockPos pos : List.of(new BlockPos(2, 2, 3), new BlockPos(2, 2, 4), new BlockPos(3, 2, 4),
                new BlockPos(4, 2, 4), new BlockPos(4, 2, 3), new BlockPos(5, 2, 3))) {
            helper.setBlock(pos, SFMBlocks.CABLE.get());
        }
        helper.setBlock(SOURCE, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(FIRST, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(HEALTHY, SFMBlocks.TEST_BARREL.get());
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
        sign(helper, new BlockPos(2, 1, 1), "RUN MANAGER", "One pulse", "Run 1 > drain", "then run 2");
        sign(helper, new BlockPos(6, 1, 1), "DRAIN SECOND", "After run 1", "Healthy only", "Then run again");
        sign(helper, new BlockPos(9, 1, 1), "MANUAL MODE", "Flick within 3s", "Otherwise auto", "Choice is latched");
        sign(helper, new BlockPos(7, 1, 5), "RUNS >= 1", "Off/off: none", "On/off: one", "On/on: two+");
        sign(helper, new BlockPos(9, 1, 5), "RUNS >= 2", "Counted tokens", "Not button taps", "Reset: rerun test");
        sign(helper, new BlockPos(1, 1, 5), "DETERMINISTIC FE", "2-FE source quantum", "Not Mekanism/JDT", "Read result signs");
        sign(helper, new BlockPos(1, 1, 1), "FIRST MACHINE", "Preparing", "", "");
        sign(helper, new BlockPos(3, 1, 1), "SECOND MACHINE", "Preparing", "", "");
        sign(helper, new BlockPos(5, 1, 5), "SOURCE", "Preparing", "", "");
        sign(helper, STATUS, "PREPARING", "Do not press yet", "", "");
        helper.runAfterDelay(2, () -> new Session(helper).configure());
    }

    private static void sign(SFMGameTestHelper helper, BlockPos pos, String... lines) {
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, Blocks.OAK_SIGN);
        text(helper, pos, lines);
    }

    @MCVersionDependentBehaviour
    private static void text(SFMGameTestHelper helper, BlockPos pos, String... lines) {
        require(lines.length <= 4, "A sign has only four lines");
        SignBlockEntity sign = helper.getBlockEntity(pos, SignBlockEntity.class);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        for (int i = 0; i < 4; i++) {
            sign.setMessage(i, Component.literal(i < lines.length ? lines[i] : ""));
        }
{% else %}
        var front = sign.getFrontText();
        for (int i = 0; i < 4; i++) {
            front = front.setMessage(i, Component.literal(i < lines.length ? lines[i] : ""));
        }
        sign.setText(front, true);
        sign.setText(front, false);
{% endcase %}
        sign.setChanged();
        BlockPos absolute = helper.absolutePos(pos);
        var state = helper.getLevel().getBlockState(absolute);
        helper.getLevel().sendBlockUpdated(absolute, state, state, 3);
    }

    private static final class Session {
        private final SFMGameTestHelper helper;
        private final ManagerBlockEntity manager;
        private final Storage source;
        private final Storage first, healthy;
        private final ChestBlockEntity tokens, counter;
        private Issue610VisualControls controls;
        private long initialFE, previousFE, selectionStarted, pendingSince;
        private int runs, pendingRun;
        private boolean drained, runPowered, drainPowered, finished;
        private Throwable failure;
        private String previousStatus = "";

        Session(SFMGameTestHelper helper) {
            this.helper = helper;
            manager = helper.getBlockEntity(MANAGER, ManagerBlockEntity.class);
            source = Issue610FixtureEnergyProvider.bind(helper.getBlockEntity(SOURCE, TestBarrelBlockEntity.class), 1000000, 1000000, 2);
            first = Issue610FixtureEnergyProvider.bind(helper.getBlockEntity(FIRST, TestBarrelBlockEntity.class), 100000, 99975, 1);
            healthy = Issue610FixtureEnergyProvider.bind(helper.getBlockEntity(HEALTHY, TestBarrelBlockEntity.class), 100000, 0, 1);
            tokens = helper.getBlockEntity(TOKENS, ChestBlockEntity.class);
            counter = helper.getBlockEntity(COUNTER, ChestBlockEntity.class);
        }

        void configure() {
            try { configureInputs(); }
            catch (RuntimeException error) { failFixture(error); }
        }

        private void configureInputs() {
            // Represents a 25-FE first-machine operation; no external machine is loaded.
            initialFE = previousFE = source.stored();
            require(source.extractForFixture(1, true) == 0 && source.extractForFixture(25, true) == 24
                    && source.extractForFixture(100000, true) == 100000, "Deterministic source quantum/supply changed");
            require(source.stored() == initialFE, "Simulation changed source FE");
            tokens.clearContent();
            counter.clearContent();
            for (int slot = 0; slot < 3; slot++) tokens.setItem(slot, new ItemStack(Items.IRON_SHOVEL));
            manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
            LabelPositionHolder.empty()
                    .add("PowerSource", helper.absolutePos(SOURCE))
                    .add("First", helper.absolutePos(FIRST))
                    .add("Healthy", helper.absolutePos(HEALTHY))
                    .add("RunTokens", helper.absolutePos(TOKENS))
                    .add("RunCounter", helper.absolutePos(COUNTER))
                    .save(Objects.requireNonNull(manager.getDisk()));
            manager.setProgram(PROGRAM);
            manager.clearRedstonePulseQueue();
            helper.assertManagerRunning(manager);
            selectionStarted = helper.getLevel().getGameTime();
            controls = new Issue610VisualControls(selectionStarted);
            // Fail and disarm before the framework's 24-hour tick deadline.
            helper.runAfterDelay(RECORDING_TICKS - 10, () -> {
                if (!finished) failFixture(new IllegalStateException("Recording tick deadline reached"));
            });
            SFM.LOGGER.info("ISSUE610_PORTABLE ready provider=sfm_fixture expected={} initialFirstFE=99975 laterFE=0 sourceFE={} selectionTicks=60 gapTicks=40 program={}",
                    EXPECT_FIXED ? "fixed_progress" : "baseline_starvation", initialFE, PROGRAM);
            helper.succeedWhen(() -> {
                poll();
                if (failure != null) {
                    helper.assertTrue(false, "Issue610 portable fixture failed: " + failure.getMessage());
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
                    require(first.stored() == 99999 && healthy.stored() == 100000,
                            "Drain requires the completed first pass with an idle 99999-FE first machine");
                    long before = source.stored();
                    require(healthy.extractForFixture(100000, false) == 100000, "Drain must remove exactly the second machine capacity");
                    drained = true;
                    require(source.stored() == before && first.stored() == 99999,
                            "Drain changed something other than the second first");
                    SFM.LOGGER.info("ISSUE610_PORTABLE drain runCount={} firstFE=99999 laterFE=0 sourceFE={} simulatedInterveningWork=true", runs, before);
                }
                var action = controls.tick(tick, manual, currentRun, currentDrain, runs, drained);
                require(controls.mode() != Issue610VisualControls.Mode.SELECTING || runs == 0,
                        "Select manual with the lever before pressing the manager during the three-second window");
                if (pendingRun != 0 && tick - pendingSince >= Issue610VisualControls.ACTION_TIMEOUT_TICKS) {
                    throw new IllegalStateException("Manager press produced no completed body within 100 ticks; inspect program/marker inventory");
                }
                require(first.stored() == (runs == 0 ? 99975 : 99999), "Idle first machine changed between passes");
                require(healthy.stored() == (runs == 0 || drained ? 0 : 100000)
                        || runs == 2 && EXPECT_FIXED && healthy.stored() == 100000,
                        "Second machine changed without the drain button or manager pass");
                require(source.stored() == previousFE, "Source changed outside a completed manager pass");
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
                    SFM.LOGGER.info("ISSUE610_PORTABLE complete mode={} actualRuns=2 firstFE={} laterFE={} sourceFE={} expected={}",
                            controls.mode(), first.stored(), healthy.stored(), source.stored(), EXPECT_FIXED);
                }
            } catch (RuntimeException error) {
                failFixture(error);
            }
        }

        private void failFixture(RuntimeException error) {
            if (failure != null) return;
            failure = error;
            finished = true;
            try {
                manager.setProgram(IDLE_PROGRAM);
                manager.clearRedstonePulseQueue();
                text(helper, STATUS, "FIXTURE FAILED", "Known runs: " + runs, "See server log", "Reset: rerun test");
            } catch (RuntimeException cleanupError) { error.addSuppressed(cleanupError); }
            source.close(); first.close(); healthy.close();
            SFM.LOGGER.error("ISSUE610_PORTABLE failed", error);
            // Outside succeedWhen's retrying assertion: this is a terminal framework failure.
            helper.runAfterDelay(1, () -> helper.assertTrue(false, "Issue610 portable fixture failed: " + error));
        }

        private void observePass(int pass) {
            require(pass == 1 || drained, "Second manager pass requires the explicit drain-button action first");
            int firstFE = first.stored(), second = healthy.stored();
            int expectedSecond = pass == 1 || EXPECT_FIXED ? 100000 : 0;
            require(firstFE == 99999 && second == expectedSecond,
                    "Pass " + pass + " expected first=99999/later=" + expectedSecond + " FE, got " + firstFE + "/" + second);
            long remaining = source.stored();
            long moved = firstFE - (pass == 1 ? 99975 : 99999) + second;
            require(previousFE - remaining == moved, "Raw FE conservation failed on pass " + pass);
            previousFE = remaining;
            require(source.extractForFixture(100000, true) == 100000,
                    "Source must still have sufficient supply; this fixture never refills it between passes");
            SFM.LOGGER.info("ISSUE610_PORTABLE pass={} actualRuns={} firstFE={} laterFE={} sourceFE={} movedFE={} initialFE={}",
                    pass, pass, firstFE, second, remaining, moved, initialFE);
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
                text(helper, new BlockPos(1, 1, 1), "FIRST MACHINE", first.stored() + "/100000 FE", "One FE stays empty", "Deterministic fixture");
                text(helper, new BlockPos(3, 1, 1), "SECOND MACHINE", healthy.stored() + "/100000 FE", "Drain after run 1", "Then run manager");
                text(helper, new BlockPos(5, 1, 5), "SOURCE", source.stored() + " FE", "2-FE quantum", "Never refilled");
                previousStatus = next;
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
