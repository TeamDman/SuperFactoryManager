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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import mezz.jei.api.recipe.RecipeType;
{% when '26.1.2' %}
{% endcase %}
import mezz.jei.api.recipe.category.IRecipeCategory;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.Holder;
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.item.Item;
{% endcase %}
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import java.util.Arrays;
{% when '26.1.2' %}
{% endcase %}

public class PrintingPressJEICategory implements IRecipeCategory<PrintingPressRecipe> {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public static final RecipeType<PrintingPressRecipe> RECIPE_TYPE = RecipeType.create(
{% when '26.1.2' %}
    public static final IRecipeType<PrintingPressRecipe> RECIPE_TYPE = IRecipeType.create(
{% endcase %}
            SFM.MOD_ID,
            "printing_press",
            PrintingPressRecipe.class
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
    private final IDrawable background;

{% when '1.21.1', '26.1.2' %}
{% endcase %}
    private final IDrawable icon;

    private final IDrawable slot;

    public PrintingPressJEICategory(IJeiHelpers jeiHelpers) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
        background = jeiHelpers.getGuiHelper().createBlankDrawable(50, 54);
{% when '1.21.1', '26.1.2' %}
{% endcase %}
        icon = jeiHelpers.getGuiHelper().createDrawableItemStack(new ItemStack(SFMBlocks.PRINTING_PRESS.get()));
        slot = jeiHelpers.getGuiHelper().getSlotDrawable();
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public RecipeType<PrintingPressRecipe> getRecipeType() {
{% when '26.1.2' %}
    public IRecipeType<PrintingPressRecipe> getRecipeType() {
{% endcase %}

        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {

        return Localization.PRINTING_PRESS_JEI_CATEGORY_TITLE.getComponent();
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
    public IDrawable getBackground() {
{% when '1.21.1', '26.1.2' %}
    public int getWidth() {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
        return background;
{% when '1.21.1', '26.1.2' %}
        return 50;
    }

    @Override
    public int getHeight() {


        return 54;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                .addItemStacks(Arrays.stream(recipe.form().getItems()).map(FormItem::createFormFromReference).toList())
{% when '26.1.2' %}
                .addItemStacks(recipe.form().getValues().stream()
                        .map(Holder::value)
                        .map(Item::asItem)
                        .map(ItemStack::new).map(FormItem::createFormFromReference).toList())
{% endcase %}
                .setBackground(slot, -1, -1);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 18).addIngredients(recipe.ink()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 36).addIngredients(recipe.paper()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 25, 18).addIngredients(recipe.form());
{% when '26.1.2' %}
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 18).add(recipe.ink()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 36).add(recipe.paper()).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 25, 18).add(recipe.form());
{% endcase %}
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
