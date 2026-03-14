package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.item.DiskItem;
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
public class DiskResetRecipe extends CustomRecipe {
    public DiskResetRecipe(ResourceLocation id) {
        super(id);
    }

    @Override
    public boolean matches(CraftingContainer pContainer, Level pLevel) {
        int foundDisks = 0;
        for (int i = 0; i < pContainer.getContainerSize(); i++) {
            ItemStack stack = pContainer.getItem(i);
            if (stack.getItem() instanceof DiskItem) {
                foundDisks++;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return foundDisks > 0;
    }

    @Override
    public ItemStack assemble(CraftingContainer pContainer) {
        int foundDisks = 0;
        for (int i = 0; i < pContainer.getContainerSize(); i++) {
            ItemStack stack = pContainer.getItem(i);
            if (stack.getItem() instanceof DiskItem) {
                foundDisks++;
            } else if (!stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return foundDisks > 0 ? new ItemStack(SFMItems.DISK_ITEM.get(), foundDisks) : ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int pWidth, int pHeight) {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SFMRecipeSerializers.DISK_RESET.get();
    }
}
