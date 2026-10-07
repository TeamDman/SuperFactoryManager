package ca.teamdman.sfm.common.program;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Context-owned one-slot storage for a materialized generated item. */
final class ProgramEphemeralItemResource implements ProgramEphemeralResource {
    private final DrainingItemStackHandler handler;
    private boolean freed;

    ProgramEphemeralItemResource(ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("A generated item resource cannot be empty");
        }
        handler = new DrainingItemStackHandler(stack);
    }

    ItemStackHandler handler() {
        return handler;
    }

    void onDrained(Runnable callback) {
        handler.onDrained = callback;
    }

    @Override
    public void free() {
        if (freed) {
            return;
        }
        freed = true;
        handler.onDrained = null;
        handler.setStackInSlot(0, ItemStack.EMPTY);
    }

    private static final class DrainingItemStackHandler extends ItemStackHandler {
        private @Nullable Runnable onDrained;

        private DrainingItemStackHandler(ItemStack stack) {
            super(1);
            setStackInSlot(0, stack);
        }

        @Override
        public @NotNull ItemStack extractItem(
                int slot,
                int amount,
                boolean simulate
        ) {
            ItemStack extracted = super.extractItem(slot, amount, simulate);
            if (!simulate && !extracted.isEmpty() && getStackInSlot(slot).isEmpty() && onDrained != null) {
                Runnable callback = onDrained;
                onDrained = null;
                callback.run();
            }
            return extracted;
        }
    }
}
