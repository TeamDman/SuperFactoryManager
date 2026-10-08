package ca.teamdman.sfm.datagen.version_plumbing;

{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.data.recipes.*;
{% when '26.1.2' %}
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
{% endcase %}
import net.minecraft.world.level.ItemLike;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.function.Consumer;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% when '26.1.2' %}
{% endcase %}

@SuppressWarnings("SameParameterValue")
public abstract class MCVersionAgnosticRecipeDataGen extends RecipeProvider {
{% case minecraft_version %}
{% when '1.19.2' %}
    public MCVersionAgnosticRecipeDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(event.getGenerator());
{% when '1.19.4', '1.20', '1.20.1' %}
    public MCVersionAgnosticRecipeDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(event.getGenerator().getPackOutput());
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public MCVersionAgnosticRecipeDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(event.getGenerator().getPackOutput(), event.getLookupProvider());
{% when '26.1.2' %}
    protected MCVersionAgnosticRecipeDataGen(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    @MCVersionDependentBehaviour
{% endcase %}
    @Override
{% case minecraft_version %}
{% when '1.19.2' %}
    protected void buildCraftingRecipes(Consumer<FinishedRecipe> writer) {
        this.populate(writer);
{% when '1.19.4', '1.20', '1.20.1' %}
    protected void buildRecipes(Consumer<FinishedRecipe> pWriter) {
        this.populate(pWriter);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    protected void buildRecipes(RecipeOutput recipeOutput) {
        this.populate(recipeOutput);
{% when '26.1.2' %}
    protected void buildRecipes() {
        this.populate(this.output);
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    protected abstract void populate(Consumer<FinishedRecipe> pConsumer);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    protected abstract void populate(RecipeOutput pConsumer);
{% when '26.1.2' %}
    protected abstract void populate(RecipeOutput output);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2' %}
    protected ShapedRecipeBuilder beginShaped(
            ItemLike result,
            int count
    ) {
        return new ShapedRecipeBuilder(result, count);
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    protected ShapedRecipeBuilder beginShaped(
            ItemLike result,
            int count
    ) {
        return new ShapedRecipeBuilder(RecipeCategory.MISC, result, count);
{% when '26.1.2' %}
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
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2' %}
    protected ShapelessRecipeBuilder beginShapeless(
            ItemLike result,
            int count
    ) {
        return new ShapelessRecipeBuilder(result, count);
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    protected ShapelessRecipeBuilder beginShapeless(
            ItemLike result,
            int count
    ) {
        return new ShapelessRecipeBuilder(RecipeCategory.MISC, result, count);
{% when '26.1.2' %}
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
{% endcase %}
    }
}
