package ca.teamdman.sfm.client.screen.workspace.timeline;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Immutable copied inventory state for one visual replay frame. Cursor coordinates are normalized to the panel. */
public record SFMInventoryReplayState(
        List<ItemStack> chestSlots,
        List<ItemStack> playerSlots,
        ItemStack cursorStack,
        double cursorPathPosition,
        String phase
) {
    public SFMInventoryReplayState {
        chestSlots = copyStacks(chestSlots);
        playerSlots = copyStacks(playerSlots);
        cursorStack = cursorStack.copy();
        if (cursorPathPosition < 0D || cursorPathPosition > 1D) {
            throw new IllegalArgumentException("Cursor path position must be normalized");
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }
}
