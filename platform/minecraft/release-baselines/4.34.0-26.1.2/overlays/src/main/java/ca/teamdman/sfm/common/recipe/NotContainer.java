package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;

/**
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
 */
@MCVersionDependentBehaviour
public interface NotContainer {
    // No longer needed in MC >= 1.21.0 - use RecipeInput instead
}
