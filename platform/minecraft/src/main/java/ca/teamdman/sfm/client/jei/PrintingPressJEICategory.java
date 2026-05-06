package ca.teamdman.sfm.client.jei;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public class PrintingPressJEICategory implements IRecipeCategory<PrintingPressRecipe> {

    public static final IRecipeType<PrintingPressRecipe> RECIPE_TYPE = IRecipeType.create(
            SFM.MOD_ID,
            "printing_press",
            PrintingPressRecipe.class
    );

    private final IDrawable icon;

    private final IDrawable slot;

    public PrintingPressJEICategory(IJeiHelpers jeiHelpers) {

        icon = jeiHelpers.getGuiHelper().createDrawableItemStack(new ItemStack(SFMBlocks.PRINTING_PRESS.get()));
        slot = jeiHelpers.getGuiHelper().getSlotDrawable();
    }

    @Override
    public IRecipeType<PrintingPressRecipe> getRecipeType() {

        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {

        return Localization.PRINTING_PRESS_JEI_CATEGORY_TITLE.getComponent();
    }

    @Override
    public int getWidth() {

        return 50;
    }

    @Override
    public int getHeight() {


        return 54;
    }

    @Override
    public IDrawable getIcon() {

        return icon;
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            PrintingPressRecipe recipe,
            IFocusGroup focuses
    ) {

        builder
                .addSlot(RecipeIngredientRole.INPUT, 0, 0)
                .addItemStacks(recipe.form().getValues().stream()
                        .map(Holder::value)
                        .map(Item::asItem)
                        .map(ItemStack::new).map(FormItem::createFormFromReference).toList())
                .setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 18).add(recipe.ink()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 36).add(recipe.paper()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 25, 18).add(recipe.form());
    }


    /// This indirection is necessary because the static fields in {@link PrintingPressJEICategory} depend on JEI code
    /// which is not present in the classpath during datagen
    public static final class Localization {

        @SFMLocalizationDatagen
        public static final LocalizationEntry PRINTING_PRESS_JEI_CATEGORY_TITLE = new LocalizationEntry(
                "gui.jei.category.sfm.printing_press",
                "Printing Press"
        );

    }

}
