package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.IRedstoneSignalStorage;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.ExecuteProgramBehaviour;
import ca.teamdman.sfm.common.program.IProgramHooks;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.linting.LabelNotConnectedProgramLinter;
import ca.teamdman.sfm.common.program.linting.ProblemTracker;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestGenerator;
import ca.teamdman.sfm.gametest.SFMGameTestGeneratorBase;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfml.ast.BoolExpr;
import ca.teamdman.sfml.ast.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Exercises labeled SFML conditions through the registered resource and network. */
@SFMGameTestGenerator
public class RedstoneQueryGameTestGenerator extends SFMGameTestGeneratorBase {
    private static final BlockPos MANAGER = new BlockPos(0, 2, 2);
    private static final BlockPos SOURCE = new BlockPos(2, 2, 2);
    private static final BlockPos SECOND = new BlockPos(3, 2, 2);
    private static final BlockPos INPUT = new BlockPos(0, 2, 1);
    private static final BlockPos OUTPUT = new BlockPos(3, 2, 0);

    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> tests) {
        for (int strength = 0; strength <= 15; strength++) {
            int expected = strength;
            add(tests, "strength_" + strength, helper -> {
                var context = setup(helper);
                comparator(helper, expected);
                assertExpr(helper, context, "abc HAS EQ " + expected + " redstone::");
                assertExpr(helper, context, "abc NULL SIDE HAS EQ " + expected + " redstone::");
                assertExpr(helper, context, "abc SOUTH SIDE HAS EQ " + expected + " redstone::");
                assertExpr(helper, context, "abc NORTH SIDE HAS EQ 0 redstone::");
                helper.succeed();
            });
        }
        for (Direction face : Direction.values()) {
            add(tests, "face_" + face.name().toLowerCase(Locale.ROOT), helper -> {
                var context = setup(helper);
                // An observer's output is opposite its FACING property.
                var observer = Blocks.OBSERVER.defaultBlockState()
                        .setValue(ObserverBlock.FACING, face.getOpposite());
                // onPlace resets initially powered observers. Change the state
                // after placement so this fixture really emits a signal.
                helper.setBlock(SOURCE, observer);
                helper.setBlock(SOURCE, observer.setValue(ObserverBlock.POWERED, true));
                helper.assertTrue(helper.getBlockState(SOURCE).getValue(ObserverBlock.POWERED),
                        "Observer fixture must be powered before querying its faces");
                for (Direction queriedFace : Direction.values()) {
                    int expected = queriedFace == face ? 15 : 0;
                    assertExpr(helper, context, "abc " + Side.fromDirection(queriedFace)
                            + " SIDE HAS EQ " + expected + " redstone::");
                }
                assertExpr(helper, context, "abc HAS EQ 15 redstone::");
                assertExpr(helper, context, "abc FRONT SIDE HAS EQ 0 redstone::");
                assertExpr(helper, context, "abc BACK SIDE HAS EQ 15 redstone::");
                helper.succeed();
            });
        }
        add(tests, "live_cached_signal", this::liveCachedSignal);
        add(tests, "lever_toggle", this::leverToggle);
        add(tests, "sources_and_sets", this::sourcesAndSets);
        add(tests, "read_only", this::readOnly);
        add(tests, "label_cleanup", this::labelCleanup);
        add(tests, "scheduled_routing", this::scheduledRouting);
        add(tests, "template", helper -> {
            try (var stream = RedstoneQueryGameTestGenerator.class.getResourceAsStream(
                    "/assets/sfm/template_programs/redstone_signals.sfml")) {
                helper.assertTrue(stream != null, "Missing redstone template resource");
                helper.compile(new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8));
                helper.succeed();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read redstone template", e);
            }
        });
    }

    private static void add(
            Consumer<SFMGameTestDefinition> tests,
            String name,
            Consumer<SFMGameTestHelper> body
    ) {
        tests.accept(new SFMGameTestDefinition() {
            @Override
            public String template() {
                return "5x2x5";
            }

            @Override
            public String testName() {
                return "redstone_query_" + name;
            }

            @Override
            public int maxTicks() {
                return 140;
            }

            @Override
            public void run(SFMGameTestHelper helper) {
                body.accept(helper);
            }
        });
    }

    private static ProgramContext setup(SFMGameTestHelper helper) {
        helper.setBlock(MANAGER, SFMBlocks.MANAGER.get());
        helper.setBlock(new BlockPos(1, 2, 2), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(1, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(2, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(SOURCE.below(), Blocks.STONE);
        var manager = helper.getBlockEntity(MANAGER, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        LabelPositionHolder.empty()
                .add("abc", helper.absolutePos(SOURCE))
                .add("signals", helper.absolutePos(SOURCE))
                .add("signals", helper.absolutePos(SECOND))
                .add("second", helper.absolutePos(SECOND))
                .add("input", helper.absolutePos(INPUT))
                .add("output", helper.absolutePos(OUTPUT))
                .add("disconnected", helper.absolutePos(new BlockPos(4, 2, 4)))
                .save(Objects.requireNonNull(manager.getDisk()));
        return new ProgramContext(helper.compile("EVERY 20 TICKS DO END"), manager, new ExecuteProgramBehaviour());
    }

    private static ComparatorBlockEntity comparator(SFMGameTestHelper helper, int strength) {
        helper.setBlock(SOURCE, Blocks.COMPARATOR.defaultBlockState()
                .setValue(ComparatorBlock.FACING, Direction.NORTH)
                .setValue(ComparatorBlock.POWERED, true));
        var comparator = helper.getBlockEntity(SOURCE, ComparatorBlockEntity.class);
        comparator.setOutputSignal(strength);
        return comparator;
    }

    private static void assertExpr(SFMGameTestHelper helper, ProgramContext context, String expression) {
        helper.assertTrue(BoolExpr.from(expression).test(context), "Condition failed: " + expression);
    }

    private static IRedstoneSignalStorage handler(ProgramContext context, BlockPos position, @Nullable Direction side) {
        return context.getNetwork().getCapability(
                SFMWellKnownCapabilities.REDSTONE_HANDLER,
                position,
                side,
                context.getLogger()
        ).unwrap();
    }

    private void liveCachedSignal(SFMGameTestHelper helper) {
        var context = setup(helper);
        var comparator = comparator(helper, 0);
        var position = helper.absolutePos(SOURCE);
        var cached = handler(context, position, null);
        var state = helper.getBlockState(SOURCE);
        assertExpr(helper, context, "abc HAS EQ 0 redstone::");
        helper.runAfterDelay(1, () -> {
            comparator.setOutputSignal(7);
            helper.assertTrue(helper.getBlockState(SOURCE) == state, "Comparator state unexpectedly changed");
            helper.assertTrue(handler(context, position, null) == cached, "Expected the same cached handler");
            helper.assertTrue(cached.getStoredAmount() == 7, "Cached handle did not observe block-entity output");
            assertExpr(helper, context, "abc HAS EQ 7 redstone::");
        });
        helper.runAfterDelay(3, () -> {
            comparator.setOutputSignal(15);
            assertExpr(helper, context, "abc HAS EQ 15 redstone::");
        });
        helper.runAfterDelay(5, () -> {
            comparator.setOutputSignal(0);
            assertExpr(helper, context, "abc HAS EQ 0 redstone::");
            helper.setBlock(SOURCE, Blocks.REDSTONE_BLOCK);
            helper.assertTrue(cached.getStoredAmount() == 15, "Retained handle did not observe replacement");
            helper.setBlock(SOURCE, Blocks.AIR);
            helper.assertTrue(cached.getStoredAmount() == 0, "Removed source retained a signal");
            helper.succeed();
        });
    }

    private void sourcesAndSets(SFMGameTestHelper helper) {
        var context = setup(helper);
        helper.setBlock(SOURCE, Blocks.REDSTONE_BLOCK);
        helper.setBlock(SECOND, Blocks.REDSTONE_BLOCK);
        assertExpr(helper, context, "abc HAS EQ 15 redstone::");
        assertExpr(helper, context, "EVERY signals HAS EQ 15 redstone::");
        assertExpr(helper, context, "OVERALL signals HAS EQ 30 redstone::");
        helper.setBlock(SECOND, Blocks.STONE);
        assertExpr(helper, context, "SOME signals HAS GT 0 redstone::");
        assertExpr(helper, context, "NOT EVERY signals HAS GT 0 redstone::");
        assertExpr(helper, context, "OVERALL signals HAS EQ 15 redstone::");
        // A solid block receiving neighbor power is not itself a signal emitter.
        assertExpr(helper, context, "second HAS EQ 0 redstone::");
        assertExpr(helper, context, "REDSTONE EQ 0");
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.REDSTONE_BLOCK);
        assertExpr(helper, context, "disconnected HAS EQ 0 redstone::");
        helper.succeed();
    }

    private void leverToggle(SFMGameTestHelper helper) {
        var context = setup(helper);
        var off = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR);
        helper.setBlock(SOURCE, off);
        var cached = handler(context, helper.absolutePos(SOURCE), null);
        assertExpr(helper, context, "abc HAS EQ 0 redstone::");
        helper.setBlock(SOURCE, off.setValue(LeverBlock.POWERED, true));
        helper.assertTrue(cached.getStoredAmount() == 15, "Retained handle missed lever switching on");
        assertExpr(helper, context, "abc HAS EQ 15 redstone::");
        helper.setBlock(SOURCE, off);
        helper.assertTrue(cached.getStoredAmount() == 0, "Retained handle missed lever switching off");
        assertExpr(helper, context, "abc HAS EQ 0 redstone::");
        helper.succeed();
    }

    private void readOnly(SFMGameTestHelper helper) {
        var context = setup(helper);
        helper.setBlock(SOURCE, Blocks.REDSTONE_BLOCK);
        var source = handler(context, helper.absolutePos(SOURCE), null);
        var type = SFMResourceTypes.REDSTONE.get();
        helper.assertTrue(!source.canExtract() && !source.canReceive(), "World signal handler must be read-only");
        helper.assertTrue(!type.canExtract(source, 0) && !type.canInsert(source, 0), "Resource advertised unsupported IO");
        for (boolean simulate : new boolean[]{true, false}) {
            helper.assertTrue(source.extract(5, simulate) == 0, "Signal extraction must reject the request");
            helper.assertTrue(source.insert(5, simulate) == 0, "Signal insertion must reject the request");
            helper.assertTrue(type.extract(source, 0, 5, simulate) == 0, "Resource extracted signal");
            helper.assertTrue(type.insert(source, 0, 5, simulate) == 5, "Resource must return all unaccepted units");
            helper.assertTrue(source.getStoredAmount() == 15, "IO changed the observed signal");
        }
        helper.assertBlockPresent(Blocks.REDSTONE_BLOCK, SOURCE);
        helper.succeed();
    }

    private void scheduledRouting(SFMGameTestHelper helper) {
        var context = setup(helper);
        helper.setBlock(INPUT, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(OUTPUT, SFMBlocks.TEST_BARREL.get());
        var lever = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR);
        helper.setBlock(SOURCE, lever);
        var input = helper.getItemHandler(INPUT);
        var output = helper.getItemHandler(OUTPUT);
        input.insertItem(0, new ItemStack(Items.STONE, 4), false);
        var manager = context.getManager();
        manager.setProgram("""
                EVERY 20 TICKS DO
                    IF abc HAS GT 0 redstone:: THEN
                        INPUT 1 stone FROM "input"
                        OUTPUT TO "output"
                    END
                END
                """);
        helper.assertManagerRunning(manager);
        manager.addProgramHooks(new IProgramHooks() {
            private int runs;

            @Override
            public void onProgramDidSomething(Duration elapsed) {
                int run = ++runs;
                helper.runAfterDelay(0, () -> {
                    int expected = run / 2;
                    helper.assertTrue(output.getStackInSlot(0).getCount() == expected,
                            "Wrong output count at trigger " + run + ": expected " + expected);
                    helper.assertTrue(input.getStackInSlot(0).getCount() == 4 - expected,
                            "Wrong input count at trigger " + run);
                    helper.assertTrue(manager.getLevel().getBestNeighborSignal(manager.getBlockPos()) == 0,
                            "Manager-local redstone must stay off");
                    if (run == 4) {
                        helper.succeed();
                    } else {
                        helper.setBlock(SOURCE, lever.setValue(LeverBlock.POWERED, run % 2 == 1));
                    }
                });
            }
        });
    }

    private void labelCleanup(SFMGameTestHelper helper) {
        var context = setup(helper);
        helper.setBlock(SOURCE, Blocks.COMPARATOR.defaultBlockState());
        helper.setBlock(SECOND, Blocks.REDSTONE_BLOCK);
        var level = helper.getLevel();
        var source = helper.absolutePos(SOURCE);
        var second = helper.absolutePos(SECOND);
        helper.assertTrue(SFMBlockCapabilityDiscovery.hasAnyCapabilityAnyDirection(level, source),
                "An unpowered signal source must be a viable label and cable connection");
        helper.assertTrue(SFMBlockCapabilityDiscovery.hasAnyCapabilityAnyDirection(level, second),
                "A redstone block must be a viable label and cable connection");
        var labels = LabelPositionHolder.empty().add("abc", source).add("second", second);
        var program = helper.compile("EVERY 20 TICKS DO IF abc HAS GT 0 redstone:: THEN END END");
        var linter = new LabelNotConnectedProgramLinter();
        var warnings = new ProblemTracker();
        linter.gatherWarnings(program, labels, context.getManager(), warnings);
        helper.assertTrue(warnings.size() == 0, "Signal source received a disconnected/invalid inventory warning");
        linter.fixWarnings(program, labels, context.getManager(), level, context.getManager().getDisk());
        helper.assertTrue(labels.labels().get("abc").contains(source) && labels.labels().get("second").contains(second),
                "Label cleanup removed a signal source");
        helper.setBlock(SOURCE, Blocks.STONE);
        helper.setBlock(SECOND, Blocks.AIR);
        helper.assertTrue(!SFMBlockCapabilityDiscovery.hasAnyCapabilityAnyDirection(level, source),
                "Ordinary stone must not become a viable inventory/cable connection");
        helper.assertTrue(!SFMBlockCapabilityDiscovery.hasAnyCapabilityAnyDirection(level, second),
                "Air must not become a viable inventory/cable connection");
        linter.fixWarnings(program, labels, context.getManager(), level, context.getManager().getDisk());
        helper.assertTrue(labels.labels().values().stream().allMatch(positions -> positions.isEmpty()),
                "Cleanup stopped removing genuinely invalid label positions");
        helper.succeed();
    }
}
