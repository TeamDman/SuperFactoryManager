package ca.teamdman.sfm;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfml.ast.Block;
import ca.teamdman.sfml.ast.Trigger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public abstract class SFMGameTestBase {
    protected static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    protected static void succeedIfManagerDidThingWithoutLagging(
            GameTestHelper helper,
            ManagerBlockEntity manager,
            Runnable assertion
    ) {
        assertManagerDidThingWithoutLagging(helper, manager, () -> {
            assertion.run();
            helper.succeed();
        });
    }


    protected static void assertManagerDidThingWithoutLagging(
            GameTestHelper helper,
            ManagerBlockEntity manager,
            Runnable assertion
    ) {
        SFMGameTestBase.assertManagerRunning(manager); // the program should already be compiled so we can monkey patch it
        var hasExecuted = new AtomicBoolean(false);
        var startTime = new AtomicLong();
        var endTime = new AtomicLong();
        @SuppressWarnings("OptionalGetWithoutIsPresent") List<Trigger> triggers = manager.getProgram().get().triggers();
        var oldFirstTrigger = triggers.get(0);
        long timeoutTicks = 200;

        Trigger startTimerTrigger = new Trigger() {
            @Override
            public boolean shouldTick(ProgramContext context) {
                return oldFirstTrigger != null
                       ? oldFirstTrigger.shouldTick(context)
                       : context.getManager().getTick() % 20 == 0;
            }

            @Override
            public void tick(ProgramContext context) {
                startTime.set(System.nanoTime());
            }

            @Override
            public Block getBlock() {
                //noinspection DataFlowIssue
                return null;
            }
        };

        Trigger endTimerTrigger = new Trigger() {
            @Override
            public boolean shouldTick(ProgramContext context) {
                return oldFirstTrigger != null
                       ? oldFirstTrigger.shouldTick(context)
                       : context.getManager().getTick() % 20 == 0;
            }

            @Override
            public void tick(ProgramContext context) {
                if (!hasExecuted.get()) {
                    hasExecuted.set(true);
                    endTime.set(System.nanoTime());
                }
            }

            @Override
            public Block getBlock() {
                //noinspection DataFlowIssue
                return null;
            }
        };

        triggers.add(0, startTimerTrigger);
        triggers.add(endTimerTrigger);

        LongStream
                .range(helper.getTick() + 1, timeoutTicks - helper.getTick())
                .forEach(i -> helper.runAfterDelay(i, () -> {
                    if (hasExecuted.get()) {
                        triggers.remove(startTimerTrigger);
                        triggers.remove(endTimerTrigger);
                        assertion.run();
                        SFMGameTestBase.assertTrue(
                                endTime.get() - startTime.get() < 70_000_000,
                                "Program took too long to run: took " + NumberFormat
                                        .getInstance(Locale.getDefault())
                                        .format(endTime.get() - startTime.get()) + "ns"
                        );
                        hasExecuted.set(false); // prevent the assertion from running again
                    }
                }));
    }

    protected static void assertManagerRunning(ManagerBlockEntity manager) {
        SFMGameTestBase.assertTrue(manager.getDisk().isPresent(), "No disk in manager");
        SFMGameTestBase.assertTrue(
                manager.getState() == ManagerBlockEntity.State.RUNNING,
                "Program did not start running " + DiskItem.getErrors(manager.getDisk().get())
        );
    }

    protected static int count(Container chest, Item item) {
        return IntStream.range(0, chest.getContainerSize())
                .mapToObj(chest::getItem)
                .filter(stack -> stack.getItem() == item)
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    protected static int count(IItemHandler chest, Item item) {
        return IntStream.range(0, chest.getSlots())
                .mapToObj(chest::getStackInSlot)
                .filter(stack -> stack.getItem() == item)
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    protected static IItemHandler getItemHandler(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper
                .getBlockEntity(pos);
        SFMGameTestBase.assertTrue(blockEntity != null, "No block entity found at " + pos);
        Optional<IItemHandler> found = blockEntity
                .getCapability(Capabilities.ITEM_HANDLER)
                .resolve();
        SFMGameTestBase.assertTrue(found.isPresent(), "No item handler found at " + pos);
        return found.get();
    }
}
