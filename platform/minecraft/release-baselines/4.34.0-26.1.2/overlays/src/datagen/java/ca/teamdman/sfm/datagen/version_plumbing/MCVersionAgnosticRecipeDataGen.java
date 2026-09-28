package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

@SuppressWarnings("SameParameterValue")
public abstract class MCVersionAgnosticRecipeDataGen extends RecipeProvider {
    protected MCVersionAgnosticRecipeDataGen(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @MCVersionDependentBehaviour
    @Override
    protected void buildRecipes() {
        this.populate(this.output);
    }

    protected abstract void populate(RecipeOutput output);

    protected Criterion<InventoryChangeTrigger.TriggerInstance> hasItem(ItemLike item) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(item);
    }

    protected Criterion<InventoryChangeTrigger.TriggerInstance> hasItem(TagKey<Item> tag) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(
                ItemPredicate.Builder.item().of(this.registries.lookupOrThrow(Registries.ITEM), tag).build()
        );
    }

    protected Ingredient ingredientFromTag(TagKey<Item> tag) {
        return Ingredient.of(this.registries.lookupOrThrow(Registries.ITEM).getOrThrow(tag));
    }

    @MCVersionDependentBehaviour
    protected ShapedRecipeBuilder beginShaped(
            ItemLike result,
            int count
    ) {
        return ShapedRecipeBuilder.shaped(
                this.registries.lookupOrThrow(Registries.ITEM),
                RecipeCategory.MISC,
                result,
                count
        );
    }

    @MCVersionDependentBehaviour
    protected ShapelessRecipeBuilder beginShapeless(
            ItemLike result,
            int count
    ) {
        return ShapelessRecipeBuilder.shapeless(
                this.registries.lookupOrThrow(Registries.ITEM),
                RecipeCategory.MISC,
                result,
                count
        );
    }
}
