package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.data.recipes.*;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.function.Consumer;

@SuppressWarnings("SameParameterValue")
public abstract class MCVersionAgnosticRecipeDataGen extends RecipeProvider {
    public MCVersionAgnosticRecipeDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(event.getGenerator().getPackOutput());
    }

    @MCVersionDependentBehaviour
    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> pWriter) {
        this.populate(pWriter);
    }

    protected abstract void populate(Consumer<FinishedRecipe> pConsumer);

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
