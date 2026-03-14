package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.registry.SFMRecipeSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Printing press copies a form using ink and paper.
 */
public class LabelGunResetRecipe extends CustomRecipe {
    public LabelGunResetRecipe(ResourceLocation id) {
        super(id);
    }

    @Override
    public boolean matches(
            CraftingContainer pContainer,
            Level pLevel
    ) {
        int foundLabelGuns = 0;
        for (int i = 0; i < pContainer.getContainerSize(); i++) {
            ItemStack stack = pContainer.getItem(i);
            if (stack.getItem() instanceof LabelGunItem) {
                foundLabelGuns++;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return foundLabelGuns > 0;
    }

    @Override
    public ItemStack assemble(CraftingContainer pContainer) {
        int foundLabelGuns = 0;
        for (int i = 0; i < pContainer.getContainerSize(); i++) {
            ItemStack stack = pContainer.getItem(i);
            if (stack.getItem() instanceof LabelGunItem) {
                foundLabelGuns++;
            } else if (!stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return foundLabelGuns > 0 ? new ItemStack(SFMItems.LABEL_GUN_ITEM.get(), foundLabelGuns) : ItemStack.EMPTY;
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
