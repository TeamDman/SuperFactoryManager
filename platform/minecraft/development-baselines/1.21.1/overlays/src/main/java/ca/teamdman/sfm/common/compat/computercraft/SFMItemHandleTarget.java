package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Resolves the concrete item stack behind a Lua handle on every mutation.
 *
 * <p>Inventory methods only expose a slot, not a durable item reference. Capturing the original
 * stack identity prevents a stale handle from mutating an unrelated item after a player or an
 * automation replaces that slot.</p>
 */
final class SFMItemHandleTarget {
    private final Supplier<Resolution> resolver;
    private final Consumer<ItemStack> onItemChanged;
    private final DiskUpdate diskUpdate;

    SFMItemHandleTarget(
            Supplier<Resolution> resolver,
            Consumer<ItemStack> onItemChanged,
            DiskUpdate diskUpdate
    ) {

        this.resolver = resolver;
        this.onItemChanged = onItemChanged;
        this.diskUpdate = diskUpdate;
    }

    static SFMItemHandleTarget inventory(
            IItemHandler inventory,
            int slot,
            ItemStack expected
    ) {

        return new SFMItemHandleTarget(
                () -> {
                    if (slot < 0 || slot >= inventory.getSlots()) {
                        return Resolution.failure("target_changed");
                    }
                    ItemStack current = inventory.getStackInSlot(slot);
                    return current == expected && !current.isEmpty()
                           ? Resolution.success(current)
                           : Resolution.failure("target_changed");
                },
                stack -> markInventoryChanged(inventory, slot, stack),
                stack -> {
                    Program program = DiskItem.compileAndUpdateErrorsAndWarnings(stack, null, true);
                    markInventoryChanged(inventory, slot, stack);
                    return program;
                }
        );
    }

    /**
     * Binds an item-slot target on its first main-thread operation. This lets a Lua factory return
     * a callable object without serialising it through CC:Tweaked's main-thread task boundary.
     */
    static SFMItemHandleTarget lazyInventory(
            IItemHandler inventory,
            int slot,
            Predicate<ItemStack> expectedType,
            String missingTypeCode
    ) {

        ItemStack[] expected = {null};
        return new SFMItemHandleTarget(
                () -> {
                    if (slot < 0 || slot >= inventory.getSlots()) {
                        return Resolution.failure("target_changed");
                    }
                    ItemStack current = inventory.getStackInSlot(slot);
                    if (expected[0] == null) {
                        if (current.isEmpty() || !expectedType.test(current)) {
                            return Resolution.failure(missingTypeCode);
                        }
                        expected[0] = current;
                        return Resolution.success(current);
                    }
                    return current == expected[0] && !current.isEmpty()
                           ? Resolution.success(current)
                           : Resolution.failure("target_changed");
                },
                stack -> markInventoryChanged(inventory, slot, stack),
                stack -> {
                    Program program = DiskItem.compileAndUpdateErrorsAndWarnings(stack, null, true);
                    markInventoryChanged(inventory, slot, stack);
                    return program;
                }
        );
    }

    private static void markInventoryChanged(
            IItemHandler inventory,
            int slot,
            ItemStack stack
    ) {

        if (inventory instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, stack);
        }
    }

    Resolution resolve() {

        try {
            return resolver.get();
        } catch (RuntimeException ignored) {
            return Resolution.failure("target_changed");
        }
    }

    void itemChanged(ItemStack stack) {

        onItemChanged.accept(stack);
    }

    @Nullable Program diskUpdated(ItemStack stack) {

        return diskUpdate.update(stack);
    }

    @FunctionalInterface
    interface DiskUpdate {
        @Nullable Program update(ItemStack stack);
    }

    record Resolution(@Nullable ItemStack stack, @Nullable String errorCode) {
        static Resolution success(ItemStack stack) {

            return new Resolution(stack, null);
        }

        static Resolution failure(String errorCode) {

            return new Resolution(null, errorCode);
        }

        boolean isResolved() {

            return stack != null;
        }
    }
}
