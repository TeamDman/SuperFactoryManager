package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.item.LabelGunItem;
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
public class LabelGunResetRecipe extends CustomRecipe {
    public LabelGunResetRecipe(
            CraftingBookCategory pCategory
    ) {
        super(pCategory);
    }

    @Override
    public boolean matches(
            CraftingInput craftingInput,
            Level level
    ) {
        int foundLabelGuns = 0;
        for (int i = 0; i < craftingInput.size(); i++) {
            ItemStack stack = craftingInput.getItem(i);
            if (stack.getItem() instanceof LabelGunItem) {
                foundLabelGuns++;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return foundLabelGuns > 0;
    }

    @Override
    public ItemStack assemble(
            CraftingInput craftingInput,
            HolderLookup.Provider provider
    ) {
        int foundLabelGuns = 0;
        for (int i = 0; i < craftingInput.size(); i++) {
            ItemStack stack = craftingInput.getItem(i);
            if (stack.getItem() instanceof LabelGunItem) {
                foundLabelGuns++;
            } else if (!stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return foundLabelGuns > 0 ? new ItemStack(SFMItems.LABEL_GUN.get(), foundLabelGuns) : ItemStack.EMPTY;
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
        return SFMRecipeSerializers.LABEL_GUN_RESET.get();
    }
}
