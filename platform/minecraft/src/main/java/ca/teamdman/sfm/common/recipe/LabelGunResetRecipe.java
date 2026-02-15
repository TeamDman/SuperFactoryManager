package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class LabelGunResetRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    @Override
    public boolean matches(InventoryCrafting inv, World worldIn) {
        int foundLabelGuns = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.getItem() instanceof LabelGunItem) {
                foundLabelGuns++;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return foundLabelGuns > 0;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        int foundLabelGuns = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.getItem() instanceof LabelGunItem) {
                foundLabelGuns++;
            } else if (!stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return foundLabelGuns > 0 ? new ItemStack(SFMItems.LABEL_GUN, foundLabelGuns) : ItemStack.EMPTY;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height > 0;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(SFMItems.LABEL_GUN);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
        return NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
    }
}