package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.BufferBlockTier;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntity;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.IRedstoneSignalStorage;
import ca.teamdman.sfm.common.capability.RedstoneSignalStorage;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Real buffer capabilities, scheduled SFML transfers, NBT, and comparator ticks. */
@SFMGameTestGenerator
public class BufferRedstoneGameTestGenerator extends SFMGameTestGeneratorBase {
    private static final BlockPos MANAGER = new BlockPos(0, 2, 2);
    private static final BlockPos DONOR = new BlockPos(0, 2, 1);
    private static final BlockPos COUNTER = new BlockPos(2, 2, 2);
    private static final BlockPos SINK = new BlockPos(3, 2, 0);
    private static final BlockPos COMPARATOR = new BlockPos(2, 2, 3);

    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> tests) {
        add(tests, "sequence", this::sequence);
        add(tests, "transfer_above_15", helper -> transfer(helper, 100, 0, 64, 36, 64));
        add(tests, "capacity_remainder", helper -> transfer(helper, 10, Integer.MAX_VALUE - 2, 10, 8, Integer.MAX_VALUE));
        add(tests, "storage_boundaries", this::storageBoundaries);
        add(tests, "resource_exclusion", this::resourceExclusion);
        add(tests, "resource_switch", this::resourceSwitch);
        add(tests, "label_cleanup", this::labelCleanup);
        add(tests, "persistence", this::persistence);
        add(tests, "load_bounds", this::loadBounds);
        add(tests, "removal", this::removal);
        add(tests, "world_rejection", this::worldRejection);
        for (int count = 0; count <= 16; count++) {
            int expected = count;
            add(tests, "comparator_" + count, helper -> {
                var context = setup(helper);
                storage(helper, COUNTER).insert(expected, false);
                helper.runAfterDelay(4, () -> {
                    assertCount(helper, context, "counter", expected);
                    assertComparator(helper, Math.min(expected, 15));
                    helper.succeed();
                });
            });
        }
        add(tests, "template", helper -> {
            helper.compile(counterProgram());
            helper.succeed();
        });
    }

    private static String counterProgram() {
        try (var stream = BufferRedstoneGameTestGenerator.class.getResourceAsStream(
                "/assets/sfm/template_programs/buffer_redstone_counter.sfml")) {
            return new String(Objects.requireNonNull(stream, "Missing buffer counter template").readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void add(Consumer<SFMGameTestDefinition> tests, String name, Consumer<SFMGameTestHelper> body) {
        tests.accept(new SFMGameTestDefinition() {
            @Override public String template() { return "5x2x5"; }
            @Override public String testName() { return "buffer_redstone_" + name; }
            @Override public int maxTicks() { return 140; }
            @Override public void run(SFMGameTestHelper helper) { body.accept(helper); }
        });
    }

    private static ProgramContext setup(SFMGameTestHelper helper) {
        helper.setBlock(MANAGER, SFMBlocks.MANAGER.get());
        helper.setBlock(new BlockPos(1, 2, 2), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(1, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(2, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 1), SFMBlocks.CABLE.get());
        for (BlockPos pos : new BlockPos[]{DONOR, COUNTER, SINK}) {
            helper.setBlock(pos, SFMBlocks.BUFFER_BLOCK.get().defaultBlockState()
                    .setValue(BufferBlock.CONTAINED_RESOURCE, BufferBlock.ContainedResource.Unknown));
        }
        helper.setBlock(COMPARATOR.below(), Blocks.STONE);
        helper.setBlock(COMPARATOR, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.NORTH));
        var manager = helper.getBlockEntity(MANAGER, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        LabelPositionHolder.empty()
                .add("donor", helper.absolutePos(DONOR))
                .add("counter", helper.absolutePos(COUNTER))
                .add("sink", helper.absolutePos(SINK))
                .add("world", helper.absolutePos(new BlockPos(4, 2, 1)))
                .save(Objects.requireNonNull(manager.getDisk()));
        return new ProgramContext(helper.compile("EVERY 20 TICKS DO END"), manager, new ExecuteProgramBehaviour());
    }

    private static IRedstoneSignalStorage storage(SFMGameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, BufferBlockEntity.class).getContents()
                .getCapability(SFMResourceTypes.REDSTONE.get()).unwrap();
    }

    private static IRedstoneSignalStorage networkStorage(SFMGameTestHelper helper, ProgramContext context) {
        return context.getNetwork().getCapability(SFMWellKnownCapabilities.REDSTONE_HANDLER,
                helper.absolutePos(COUNTER), null, context.getLogger()).unwrap();
    }

    private static void assertCount(SFMGameTestHelper helper, ProgramContext context, String label, int expected) {
        String condition = label + " HAS EQ " + expected + " redstone::";
        helper.assertTrue(BoolExpr.from(condition).test(context), "Condition failed: " + condition);
    }

    private static void assertComparator(SFMGameTestHelper helper, int expected) {
        int actual = helper.getBlockEntity(COMPARATOR, ComparatorBlockEntity.class).getOutputSignal();
        helper.assertTrue(actual == expected, "Comparator: expected " + expected + ", got " + actual);
        int emitted = helper.getBlockState(COMPARATOR).getSignal(helper.getLevel(), helper.absolutePos(COMPARATOR), Direction.NORTH);
        helper.assertTrue(emitted == expected, "Comparator did not emit the measured count");
    }

    private void sequence(SFMGameTestHelper helper) {
        var context = setup(helper);
        storage(helper, DONOR).insert(2, false);
        var cached = networkStorage(helper, context);
        helper.assertTrue(cached == storage(helper, COUNTER), "World signal provider hid buffer storage");
        assertCount(helper, context, "counter", 0);
        assertComparator(helper, 0);
        var manager = context.getManager();
        manager.setProgram(counterProgram());
        helper.assertManagerRunning(manager);
        manager.addProgramHooks(new IProgramHooks() {
            private int runs;

            @Override
            public void onProgramDidSomething(Duration elapsed) {
                int run = ++runs;
                helper.runAfterDelay(4, () -> {
                    int expected = new int[]{0, 1, 2, 1, 0}[run];
                    assertCount(helper, context, "counter", expected);
                    assertCount(helper, context, "donor", Math.max(0, 2 - run));
                    assertCount(helper, context, "sink", Math.max(0, run - 2));
                    helper.assertTrue(cached.getStoredAmount() == expected, "Retained handler missed transfer");
                    assertComparator(helper, expected);
                    if (run == 4) helper.succeed();
                });
            }
        });
    }

    private void transfer(SFMGameTestHelper helper, int donor, int counter, int requested, int donorAfter, int counterAfter) {
        var context = setup(helper);
        storage(helper, DONOR).insert(donor, false);
        storage(helper, COUNTER).insert(counter, false);
        var manager = context.getManager();
        manager.setProgram("EVERY 20 TICKS DO INPUT " + requested + " redstone:: FROM donor OUTPUT redstone:: TO counter END");
        helper.assertManagerRunning(manager);
        manager.addProgramHooks(new IProgramHooks() {
            @Override public void onProgramDidSomething(Duration elapsed) {
                helper.runAfterDelay(4, () -> {
                    assertCount(helper, context, "donor", donorAfter);
                    assertCount(helper, context, "counter", counterAfter);
                    helper.assertTrue((long) donor + counter == (long) donorAfter + counterAfter, "Transfer lost or created units");
                    assertComparator(helper, Math.min(counterAfter, 15));
                    helper.succeed();
                });
            }
        });
    }

    private void storageBoundaries(SFMGameTestHelper helper) {
        var changes = new AtomicInteger();
        var storage = new RedstoneSignalStorage(0, 20) {
            @Override protected void onContentsChanged() { changes.incrementAndGet(); }
        };
        var type = SFMResourceTypes.REDSTONE.get();
        helper.assertTrue(type.canInsert(storage, 0) && type.canExtract(storage, 0), "Mutable storage must advertise IO");
        helper.assertTrue(type.getMaxStackSizeForSlot(storage, 0) == 20, "Capacity was capped at signal strength");
        helper.assertTrue(type.insert(storage, 0, 25, true) == 5, "Simulation returned wrong remainder");
        helper.assertTrue(storage.getStoredAmount() == 0 && changes.get() == 0, "Simulation mutated storage");
        helper.assertTrue(type.insert(storage, 0, 25, false) == 5, "Actual insert returned wrong remainder");
        helper.assertTrue(storage.getStoredAmount() == 20 && changes.get() == 1, "Insert must notify once");
        helper.assertTrue(type.extract(storage, 0, Long.MAX_VALUE, true) == 20, "Long extraction overflowed");
        helper.assertTrue(type.extract(storage, 0, Long.MIN_VALUE, false) == 0, "Negative extraction changed storage");
        helper.assertTrue(storage.insert(-1, false) == 0 && storage.insert(1, false) == 0, "Invalid/full insert accepted");
        helper.assertTrue(storage.getStoredAmount() == 20 && changes.get() == 1, "No-op/simulation notified");
        helper.assertTrue(type.extract(storage, 0, Long.MAX_VALUE, false) == 20, "Actual extraction lost units");
        helper.assertTrue(storage.extract(1, false) == 0 && changes.get() == 2, "Empty extraction notified");
        storage.deserializeNBT(IntTag.valueOf(Integer.MAX_VALUE));
        helper.assertTrue(storage.getStoredAmount() == 20, "Loaded value exceeded capacity");
        storage.deserializeNBT(IntTag.valueOf(-1));
        helper.assertTrue(storage.getStoredAmount() == 0, "Loaded value underflowed");
        var largest = new RedstoneSignalStorage(0, Integer.MAX_VALUE);
        helper.assertTrue(largest.insert(Integer.MAX_VALUE, false) == Integer.MAX_VALUE, "Maximum capacity insert failed");
        helper.assertTrue(largest.insert(1, false) == 0 && largest.getStoredAmount() == Integer.MAX_VALUE, "Storage overflowed");
        helper.succeed();
    }

    private void resourceExclusion(SFMGameTestHelper helper) {
        var changes = new AtomicInteger();
        var contents = new BufferBlockEntityContents(BufferBlockTier.Basic, changes::incrementAndGet);
        var type = SFMResourceTypes.REDSTONE.get();
        var redstone = contents.getCapability(type).unwrap();
        var items = contents.getCapability(SFMResourceTypes.ITEM.get()).unwrap();
        helper.assertTrue(type.canInsert(redstone, 0), "Empty buffer must accept redstone");
        helper.assertTrue(type.insert(redstone, 0, 1, true) == 0, "Empty buffer simulation rejected units");
        helper.assertTrue(contents.lastUsedResource == BufferBlock.ContainedResource.Unknown && changes.get() == 0,
                "Capability query/simulation changed icon or notified owner");
        items.insertItem(0, new ItemStack(Items.STONE), false);
        helper.assertTrue(!type.canInsert(redstone, 0) && type.insert(redstone, 0, 1, false) == 1,
                "Cached redstone handler allowed mixed resources");
        helper.assertTrue(redstone.getStoredAmount() == 0, "Rejected insertion changed counter");
        contents.loadRedstone(8);
        helper.assertTrue(redstone.getStoredAmount() == 0 && items.getStackInSlot(0).getCount() == 1,
                "Loading redstone overwrote an occupied resource");
        items.extractItem(0, 1, false);
        helper.assertTrue(type.insert(redstone, 0, 2, false) == 0, "Emptied buffer stayed locked to items");
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.STONE), false).getCount() == 1,
                "Cached item handler allowed mixed resources");
        helper.assertTrue(contents.lastUsedResource == BufferBlock.ContainedResource.Redstone, "Real insert did not select redstone icon");
        type.extract(redstone, 0, 2, false);
        helper.assertTrue(contents.lastUsedResource == BufferBlock.ContainedResource.Unknown && changes.get() == 2,
                "Counter emptying did not notify/reset icon");
        helper.succeed();
    }

    private void persistence(SFMGameTestHelper helper) {
        var context = setup(helper);
        storage(helper, COUNTER).insert(37, false);
        var original = helper.getBlockEntity(COUNTER, BufferBlockEntity.class);
        var saved = original.saveWithFullMetadata();
        helper.assertTrue(saved.getInt("redstone") == 37, "Save omitted counter units");
        // Cache before replacing the entity to test invalidation and rediscovery.
        var oldHandle = networkStorage(helper, context);
        var restored = BlockEntity.loadStatic(helper.absolutePos(COUNTER), helper.getBlockState(COUNTER), saved);
        helper.assertTrue(restored instanceof BufferBlockEntity, "Registered block-entity NBT reload failed");
        helper.getLevel().removeBlockEntity(helper.absolutePos(COUNTER));
        helper.getLevel().setBlockEntity(Objects.requireNonNull(restored));
        var cached = networkStorage(helper, context);
        helper.assertTrue(cached != oldHandle && cached.getStoredAmount() == 37, "Reload reused detached storage");
        helper.runAfterDelay(4, () -> {
            assertCount(helper, context, "counter", 37);
            assertComparator(helper, 15);
            var eight = restored.saveWithFullMetadata();
            eight.putInt("redstone", 8);
            restored.load(eight);
            helper.assertTrue(cached.getStoredAmount() == 8, "NBT load orphaned cached storage");
        });
        helper.runAfterDelay(8, () -> {
            assertCount(helper, context, "counter", 8);
            assertComparator(helper, 8);
            restored.load(new CompoundTag());
        });
        helper.runAfterDelay(12, () -> {
            assertCount(helper, context, "counter", 0);
            assertComparator(helper, 0);
            helper.assertTrue(restored.saveWithFullMetadata().getInt("redstone") == 0, "Empty reload retained saved count");
            helper.succeed();
        });
    }

    private void resourceSwitch(SFMGameTestHelper helper) {
        var context = setup(helper);
        var items = helper.getItemHandler(COUNTER);
        items.insertItem(0, new ItemStack(Items.STONE), false);
        assertCount(helper, context, "counter", 0);
        items.extractItem(0, 1, false);
        storage(helper, COUNTER).insert(4, false);
        assertCount(helper, context, "counter", 4);
        helper.assertTrue(networkStorage(helper, context).canExtract(), "Cached world fallback hid newly available storage");
        helper.runAfterDelay(4, () -> {
            assertComparator(helper, 4);
            helper.succeed();
        });
    }

    private void labelCleanup(SFMGameTestHelper helper) {
        var context = setup(helper);
        storage(helper, COUNTER).insert(21, false);
        var pos = helper.absolutePos(COUNTER);
        helper.assertTrue(SFMBlockCapabilityDiscovery.hasAnyCapabilityAnyDirection(helper.getLevel(), pos),
                "Filled redstone buffer lost label/cable eligibility");
        var labels = LabelPositionHolder.empty().add("counter", pos);
        var program = helper.compile("EVERY 20 TICKS DO IF counter HAS GT 0 redstone:: THEN END END");
        var linter = new LabelNotConnectedProgramLinter();
        var warnings = new ProblemTracker();
        linter.gatherWarnings(program, labels, context.getManager(), warnings);
        helper.assertTrue(warnings.size() == 0, "Filled buffer was reported disconnected");
        linter.fixWarnings(program, labels, context.getManager(), helper.getLevel(), context.getManager().getDisk());
        helper.assertTrue(labels.labels().get("counter").contains(pos), "Label cleanup removed a buffer counter");
        helper.succeed();
    }

    private void loadBounds(SFMGameTestHelper helper) {
        var context = setup(helper);
        var buffer = helper.getBlockEntity(COUNTER, BufferBlockEntity.class);
        var data = new CompoundTag();
        data.putInt("redstone", Integer.MAX_VALUE);
        buffer.load(data);
        assertCount(helper, context, "counter", Integer.MAX_VALUE);
        data.putLong("redstone", Long.MAX_VALUE);
        buffer.load(data);
        assertCount(helper, context, "counter", Integer.MAX_VALUE);
        data.putLong("redstone", Long.MIN_VALUE);
        buffer.load(data);
        assertCount(helper, context, "counter", 0);
        data.putString("redstone", "invalid");
        buffer.load(data);
        assertCount(helper, context, "counter", 0);
        helper.succeed();
    }

    private void removal(SFMGameTestHelper helper) {
        setup(helper);
        storage(helper, COUNTER).insert(9, false);
        helper.runAfterDelay(4, () -> {
            assertComparator(helper, 9);
            helper.setBlock(COUNTER, Blocks.AIR);
        });
        helper.runAfterDelay(8, () -> {
            assertComparator(helper, 0);
            helper.succeed();
        });
    }

    private void worldRejection(SFMGameTestHelper helper) {
        var context = setup(helper);
        helper.setBlock(new BlockPos(4, 2, 1), Blocks.REDSTONE_BLOCK);
        storage(helper, DONOR).insert(3, false);
        var manager = context.getManager();
        manager.setProgram("""
                EVERY 20 TICKS DO
                    INPUT 3 redstone:: FROM world
                    OUTPUT redstone:: TO counter
                    FORGET
                    INPUT 3 redstone:: FROM donor
                    OUTPUT redstone:: TO world
                    OUTPUT redstone:: TO sink
                END
                """);
        helper.assertManagerRunning(manager);
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            assertCount(helper, context, "counter", 0);
            assertCount(helper, context, "donor", 0);
            assertCount(helper, context, "sink", 3);
            assertCount(helper, context, "world", 15);
        });
    }
}
