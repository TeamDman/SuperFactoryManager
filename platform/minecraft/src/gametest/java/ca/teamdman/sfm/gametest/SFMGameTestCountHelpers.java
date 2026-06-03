package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.common.util.SFMItemUtils;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public class SFMGameTestCountHelpers {
    // Container
    public static int count(Container inventory) {

        return count(inventory, (ItemLike) null);
    }

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
            Container inventory,
            ItemStack comparisonStack
    ) {

        return IntStream.range(0, inventory.getContainerSize())
                .mapToObj(inventory::getItem)
                .filter(stack -> SFMItemUtils.isSameItemSameTags(stack, comparisonStack))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    // ResourceHandler
    public static int count(ResourceHandler<ItemResource> inventory) {

        return count(inventory, (ItemLike) null);
    }

    public static int count(
            ResourceHandler<ItemResource> inventory,
            @Nullable ItemLike item
    ) {

        return IntStream.range(0, inventory.size())
                .mapToObj(inventory::getAmountAsInt)
                .mapToInt(Integer::intValue)
                .sum();
    }

    public static int count(
            ResourceHandler<ItemResource> inventory,
            ItemResource comparisonStack
    ) {
        return IntStream.range(0, inventory.size())
                .filter(i -> inventory.getResource(i).equals(comparisonStack))
                .map(inventory::getAmountAsInt)
                .sum();
    }

    // IItemHandler
    public static int count(IItemHandler inventory) {

        return count(inventory, (ItemLike) null);
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
            IItemHandler inventory,
            ItemStack comparisonStack
    ) {

        return IntStream.range(0, inventory.getSlots())
                .mapToObj(inventory::getStackInSlot)
                .filter(stack -> SFMItemUtils.isSameItemSameTags(stack, comparisonStack))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    // Container
    public static void assertCount(
            Container inventory,
            int expectedCount,
            String message,
            long tick
    ) {

        assertCount(inventory, (ItemLike) null, expectedCount, message, tick);
    }

    public static void assertCount(
            Container inventory,
            @Nullable ItemLike item,
            int expectedCount,
            String message,
            long tick
    ) {

        int actualCount = count(inventory, item);
        SFMGameTestMethodHelpers.assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount,
                tick
        );
    }

    public static void assertCount(
            Container inventory,
            ItemStack comparisonStack,
            int expectedCount,
            String message,
            long tick
    ) {

        int actualCount = count(inventory, comparisonStack);
        SFMGameTestMethodHelpers.assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount,
                tick
        );
    }

    // ResourceHandler
    public static void assertCount(
            ResourceHandler<ItemResource> inventory,
            int expectedCount,
            String message,
            long tick
    ) {

        assertCount(inventory, (ItemLike) null, expectedCount, message, tick);
    }

    public static void assertCount(
            ResourceHandler<ItemResource> inventory,
            @Nullable ItemLike item,
            int expectedCount,
            String message,
            long tick
    ) {

        int actualCount = count(inventory, item);
        SFMGameTestMethodHelpers.assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount,
                tick
        );
    }

    public static void assertCount(
            ResourceHandler<ItemResource> inventory,
            ItemStack comparisonStack,
            int expectedCount,
            String message,
            long tick
    ) {

        int actualCount = count(inventory, ItemResource.of(comparisonStack));
        SFMGameTestMethodHelpers.assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount,
                tick
        );
    }

    // IItemHandler
    public static void assertCount(
            IItemHandler inventory,
            int expectedCount,
            String message,
            long tick
    ) {

        assertCount(inventory, (ItemLike) null, expectedCount, message, tick);
    }

    public static void assertCount(
            IItemHandler inventory,
            @Nullable ItemLike item,
            int expectedCount,
            String message,
            long tick
    ) {

        int actualCount = count(inventory, item);
        SFMGameTestMethodHelpers.assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount,
                tick
        );
    }

    public static void assertCount(
            IItemHandler inventory,
            ItemStack comparisonStack,
            int expectedCount,
            String message,
            long tick
    ) {

        int actualCount = count(inventory, comparisonStack);
        SFMGameTestMethodHelpers.assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount,
                tick
        );
    }

    public static void assertCount(
            AtomicReference<?> ref,
            int expectedCount,
            String message,
            long tick
    ) {

        var inventory = ref.get();
        if (inventory instanceof Container container) {
            assertCount(container, expectedCount, message, tick);
        } else if (inventory instanceof ResourceHandler<?> resourceHandler) {
            assertCount((ResourceHandler<ItemResource>) resourceHandler, expectedCount, message, tick);
        } else if (inventory instanceof IItemHandler itemHandler) {
            assertCount(itemHandler, expectedCount, message, tick);
        } else {
            throw new IllegalArgumentException("Expected either a Container, IItemHandler, or ResourceHandler<ItemResource> but got "
                                               + inventory.getClass());
        }
    }

}
