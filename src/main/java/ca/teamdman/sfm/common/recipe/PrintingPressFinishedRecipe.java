package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.registry.SFMRecipeSerializers;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class PrintingPressFinishedRecipe implements FinishedRecipe {
    private final ResourceLocation id;
    private final Ingredient form;
    private final Ingredient ink;
    private final Ingredient paper;

    public PrintingPressFinishedRecipe(
            ResourceLocation id,
            Ingredient form,
            Ingredient ink,
            Ingredient paper
    ) {
        this.id = id;
        this.form = form;
        this.ink = ink;
        this.paper = paper;
    }

    @Override
    public void serializeRecipeData(JsonObject json) {
        json.add("form", form.toJson());
        json.add("ink", ink.toJson());
        json.add("paper", paper.toJson());
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getType() {
        return SFMRecipeSerializers.PRINTING_PRESS.get();
    }

    @Override
    public JsonObject serializeAdvancement() {
        return null; // No advancements needed for this recipe
    }

    @Override
    public ResourceLocation getAdvancementId() {
        return SFMResourceLocation.fromMinecraftPath("");
    }
}
