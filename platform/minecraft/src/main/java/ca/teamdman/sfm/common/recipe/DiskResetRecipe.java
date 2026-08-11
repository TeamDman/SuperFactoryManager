package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Printing press copies a form using ink and paper.
 */
public class DiskResetRecipe extends CustomRecipe {
    public DiskResetRecipe(CraftingBookCategory pGroup) {
        super(pGroup);
    }

    public int countDisks(CraftingInput input) {
        int found = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof DiskItem) {
                found++;
            } else {
                return -1;
            }
        }
        return found;
    }

    @Override
    public boolean matches(
            CraftingInput craftingInput,
            Level pLevel
    ) {
        return countDisks(craftingInput) > 0;
    }

    @Override
    public ItemStack assemble(
            CraftingInput craftingInput,
            HolderLookup.Provider provider
    ) {
        int foundDisks = countDisks(craftingInput);
        if (foundDisks > 0) {
            return new ItemStack(SFMItems.DISK.get(), foundDisks);
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean canCraftInDimensions(
            int pWidth,
            int pHeight
    ) {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SFMRecipeSerializers.DISK_RESET.get();
    }
}
