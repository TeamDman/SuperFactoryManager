package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.data.recipes.*;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@SuppressWarnings("SameParameterValue")
public abstract class MCVersionAgnosticRecipeDataGen extends RecipeProvider {
    public MCVersionAgnosticRecipeDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(event.getGenerator().getPackOutput(), event.getLookupProvider());
    }

    @MCVersionDependentBehaviour
    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        this.populate(recipeOutput);
    }

    protected abstract void populate(RecipeOutput pConsumer);

    protected ShapedRecipeBuilder beginShaped(
            ItemLike result,
            int count
    ) {
        return new ShapedRecipeBuilder(RecipeCategory.MISC, result, count);
    }

    protected ShapelessRecipeBuilder beginShapeless(
            ItemLike result,
            int count
    ) {
        return new ShapelessRecipeBuilder(RecipeCategory.MISC, result, count);
    }
}
