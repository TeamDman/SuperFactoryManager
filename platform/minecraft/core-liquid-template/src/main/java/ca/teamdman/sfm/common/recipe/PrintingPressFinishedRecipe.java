package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
{% when '1.20.2' %}
{% endcase %}
import com.google.gson.JsonObject;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
{% when '1.20.2' %}
import net.minecraft.advancements.AdvancementHolder;
{% endcase %}
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
{% when '1.20.2' %}
import org.jetbrains.annotations.Nullable;
{% endcase %}

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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        json.add("form", form.toJson());
        json.add("ink", ink.toJson());
        json.add("paper", paper.toJson());
{% when '1.20.2' %}
        json.add("form", form.toJson(false));
        json.add("ink", ink.toJson(false));
        json.add("paper", paper.toJson(false));
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public ResourceLocation getId() {
{% when '1.20.2' %}
    public ResourceLocation id() {
{% endcase %}
        return id;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public RecipeSerializer<?> getType() {
{% when '1.20.2' %}
    public RecipeSerializer<?> type() {
{% endcase %}
        return SFMRecipeSerializers.PRINTING_PRESS.get();
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public JsonObject serializeAdvancement() {
        return null; // No advancements needed for this recipe
{% when '1.20.2' %}
    public JsonObject serializeRecipe() {
        return FinishedRecipe.super.serializeRecipe();
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
{% when '1.20.2' %}
    @Nullable
{% endcase %}
    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public ResourceLocation getAdvancementId() {
        return SFMResourceLocation.fromMinecraftPath("");
{% when '1.20.2' %}
    public AdvancementHolder advancement() {
        return null;
{% endcase %}
    }
}
