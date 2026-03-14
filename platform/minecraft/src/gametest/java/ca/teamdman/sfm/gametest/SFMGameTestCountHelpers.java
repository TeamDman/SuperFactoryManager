package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.common.util.SFMItemUtils;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public class SFMGameTestCountHelpers {
    public static int count(
            Container inventory,
            @Nullable ItemLike item
    ) {

        return IntStream.range(0, inventory.getContainerSize())
                .mapToObj(inventory::getItem)
                .filter(stack -> item == null || stack.getItem() == item.asItem())
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static int count(
            IItemHandler inventory,
            @Nullable ItemLike item
    ) {

        return IntStream.range(0, inventory.getSlots())
                .mapToObj(inventory::getStackInSlot)
                .filter(stack -> item == null || stack.getItem() == item.asItem())
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static int count(
            Container inventory,
            ItemStack comparisonStack
    ) {

        return IntStream.range(0, inventory.getContainerSize())
                .mapToObj(inventory::getItem)
                .filter(stack -> SFMItemUtils.isSameItemSameTags(stack, comparisonStack))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static int count(
            IItemHandler inventory,
            ItemStack comparisonStack
    ) {

        return IntStream.range(0, inventory.getSlots())
                .mapToObj(inventory::getStackInSlot)
                .filter(stack -> SFMItemUtils.isSameItemSameTags(stack, comparisonStack))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static int count(Container inventory) {

        return count(inventory, (ItemLike) null);
    }

    public static int count(IItemHandler inventory) {

        return count(inventory, (ItemLike) null);
    }

    public static void assertCount(
            IItemHandler inventory,
            @Nullable ItemLike item,
            int expectedCount,
            String message
    ) {

        int actualCount = count(inventory, item);
        SFMGameTestMethodHelpers.assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount
        );
    }

    public static void assertCount(
            Container inventory,
            @Nullable ItemLike item,
            int expectedCount,
            String message
    ) {

        int actualCount = count(inventory, item);
        SFMGameTestMethodHelpers.assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount
        );
    }

    public static void assertCount(
            Container inventory,
            ItemStack comparisonStack,
            int expectedCount,
            String message
    ) {

        int actualCount = count(inventory, comparisonStack);
        SFMGameTestMethodHelpers.assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount
        );
    }

    public static void assertCount(
            IItemHandler inventory,
            ItemStack comparisonStack,
            int expectedCount,
            String message
    ) {

        int actualCount = count(inventory, comparisonStack);
        SFMGameTestMethodHelpers.assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount
        );
    }

    public static void assertCount(
            IItemHandler inventory,
            int expectedCount,
            String message
    ) {

        assertCount(inventory, (ItemLike) null, expectedCount, message);
    }

    public static void assertCount(
            Container inventory,
            int expectedCount,
            String message
    ) {

        assertCount(inventory, (ItemLike) null, expectedCount, message);
    }

    public static void assertCount(
            AtomicReference<?> ref,
            int expectedCount,
            String message
    ) {

        var inventory = ref.get();
        if (inventory instanceof Container container) {
            assertCount(container, expectedCount, message);
        } else if (inventory instanceof IItemHandler itemHandler) {
            assertCount(itemHandler, expectedCount, message);
        } else {
            throw new IllegalArgumentException("Expected either a Container or IItemHandler but got "
                                               + inventory.getClass());
        }
    }

}
