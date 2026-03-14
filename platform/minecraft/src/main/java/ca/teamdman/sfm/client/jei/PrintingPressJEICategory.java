package ca.teamdman.sfm.client.jei;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public class PrintingPressJEICategory implements IRecipeCategory<PrintingPressRecipe> {

    public static final RecipeType<PrintingPressRecipe> RECIPE_TYPE = RecipeType.create(
            SFM.MOD_ID,
            "printing_press",
            PrintingPressRecipe.class
    );
    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;

    public PrintingPressJEICategory(IJeiHelpers jeiHelpers) {
        background = jeiHelpers.getGuiHelper().createBlankDrawable(50, 54);
        icon = jeiHelpers.getGuiHelper().createDrawableItemStack(new ItemStack(SFMBlocks.PRINTING_PRESS_BLOCK.get()));
        slot = jeiHelpers.getGuiHelper().getSlotDrawable();
    }

    @Override
    public RecipeType<PrintingPressRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return LocalizationKeys.PRINTING_PRESS_JEI_CATEGORY_TITLE.getComponent();
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PrintingPressRecipe recipe, IFocusGroup focuses) {
        builder
                .addSlot(RecipeIngredientRole.INPUT, 0, 0)
                .addItemStacks(Arrays.stream(recipe.form().getItems()).map(FormItem::createFormFromReference).toList())
                .setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 18).addIngredients(recipe.ink()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 36).addIngredients(recipe.paper()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 25, 18).addIngredients(recipe.form());
    }
}
