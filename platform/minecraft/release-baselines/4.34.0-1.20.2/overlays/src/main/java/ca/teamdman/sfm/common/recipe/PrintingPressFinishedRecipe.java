package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.Nullable;

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
        json.add("form", form.toJson(false));
        json.add("ink", ink.toJson(false));
        json.add("paper", paper.toJson(false));
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public RecipeSerializer<?> type() {
        return SFMRecipeSerializers.PRINTING_PRESS.get();
    }

    @Override
    public JsonObject serializeRecipe() {
        return FinishedRecipe.super.serializeRecipe();
    }

    @Nullable
    @Override
    public AdvancementHolder advancement() {
        return null;
    }
}
