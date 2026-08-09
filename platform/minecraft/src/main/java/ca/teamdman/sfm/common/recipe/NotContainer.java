package ca.teamdman.sfm.common.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Recipe stuff wants your block entities to be Containers to do stuff.
 * I don't want to use a Container when the block has no GUI.
 * This is a hack to make the recipe stuff happy.
 */
@SuppressWarnings("RedundantMethodOverride")
public interface NotContainer extends Container {

    @Override
    default void clearContent() {
    }

    @Override
    default int getContainerSize() {
        return 0;
    }

    @Override
    default boolean isEmpty() {
        return true;
    }

    @Override
    default ItemStack getItem(int pSlot) {
        return ItemStack.EMPTY;
    }

    @Override
    default ItemStack removeItem(int pSlot, int pAmount) {
        return ItemStack.EMPTY;

    }

    @Override
    default ItemStack removeItemNoUpdate(int pSlot) {
        return ItemStack.EMPTY;
    }

    @Override
    default void setItem(int pSlot, ItemStack pStack) {
    }

    @Override
    default int getMaxStackSize() {
        return 0;
    }

    @Override
    default void setChanged() {
    }

    @Override
    default boolean stillValid(Player pPlayer) {
        return false;
    }

    @Override
    default void startOpen(Player pPlayer) {
    }

    @Override
    default void stopOpen(Player pPlayer) {
    }

    @Override
    default boolean canPlaceItem(int pIndex, ItemStack pStack) {
        return false;
    }

    @Override
    default int countItem(Item pItem) {
        return 0;
    }

    @Override
    default boolean hasAnyOf(Set<Item> pSet) {
        return false;
    }

    @Override
    default boolean hasAnyMatching(Predicate<ItemStack> p_216875_) {
        return false;
    }
}
