package ca.teamdman.sfm.datagen.version_plumbing;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.function.Consumer;

@SuppressWarnings("SameParameterValue")
public abstract class MCVersionAgnosticRecipeDataGen extends RecipeProvider {
    public MCVersionAgnosticRecipeDataGen(GatherDataEvent event, String modId) {
        super(event.getGenerator());
    }

    @Override
    protected void buildCraftingRecipes(Consumer<FinishedRecipe> writer) {
        this.populate(writer);
    }

    protected abstract void populate(Consumer<FinishedRecipe> pConsumer);

    protected ShapedRecipeBuilder beginShaped(ItemLike result, int count) {
        return new ShapedRecipeBuilder(result, count);
    }

    protected ShapelessRecipeBuilder beginShapeless(ItemLike result, int count) {
        return new ShapelessRecipeBuilder(result, count);
    }
}
