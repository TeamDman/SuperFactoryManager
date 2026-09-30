package ca.teamdman.sfm.common.recipe;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.function.Predicate;
{% when '1.21', '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}

/**
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
 * Recipe stuff wants your block entities to be Containers to do stuff.
 * I don't want to use a Container when the block has no GUI.
 * This is a hack to make the recipe stuff happy.
{% when '1.21', '1.21.1', '26.1.2' %}
 * In MC < 1.21.0, this interface was used as a hack to satisfy the recipe system
 * which required block entities to implement {@link net.minecraft.world.Container}
 * even when the block has no GUI.
 * <p>
 * In MC >= 1.21.0, Minecraft introduced {@link net.minecraft.world.item.crafting.RecipeInput}
 * which provides a proper way to handle recipe inputs without needing to implement Container.
 * {@link ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity} now implements
 * RecipeInput directly instead of NotContainer.
 * <p>
 * This stub interface is kept for propagation compatibility between version branches.
 *
 * @see net.minecraft.world.item.crafting.RecipeInput
{% endcase %}
 */
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
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
{% when '1.21', '1.21.1', '26.1.2' %}
@MCVersionDependentBehaviour
public interface NotContainer {
    // No longer needed in MC >= 1.21.0 - use RecipeInput instead
{% endcase %}
}
