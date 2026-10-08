package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeTypes;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2' %}
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
{% when '1.19.4', '1.20', '1.20.1' %}
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
{% when '1.20.2', '1.20.3', '1.20.4' %}
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1' %}
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
{% when '26.1.2' %}
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
{% endcase %}
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
{% when '26.1.2' %}
import net.minecraft.world.item.crafting.*;
{% endcase %}
import net.minecraft.world.level.Level;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import org.jetbrains.annotations.Nullable;

{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import org.jspecify.annotations.NonNull;

{% endcase %}
import java.util.Objects;

/**
 * Printing press copies a form using ink and paper.
 */
public record PrintingPressRecipe(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        @MCVersionDependentBehaviour
        ResourceLocation id,

{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
        Ingredient form,
        Ingredient ink,
        Ingredient paper
) implements Recipe<PrintingPressBlockEntity> {
    @Override
    public boolean matches(
            PrintingPressBlockEntity pContainer,
            Level pLevel
    ) {
        return paper.test(pContainer.getPaper())
               && ink.test(pContainer.getInk())
               && form.test(FormItem.getBorrowedReferenceFromForm(pContainer.getForm()));
    }

    @MCVersionDependentBehaviour
    @Override
{% case minecraft_version %}
{% when '1.19.2' %}
    public ItemStack assemble(
            PrintingPressBlockEntity pContainer
    ) {
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public ItemStack assemble(
            PrintingPressBlockEntity pContainer,
            RegistryAccess p_267165_
    ) {
{% when '1.21', '1.21.1' %}
    public ItemStack assemble(
            PrintingPressBlockEntity pContainer,
            HolderLookup.Provider provider
    ) {
{% when '26.1.2' %}
    public ItemStack assemble(
            PrintingPressBlockEntity pContainer
    ) {
{% endcase %}
        ItemStack rtn = FormItem.getCopiedReferenceFromForm(pContainer.getForm());
        rtn.setCount(pContainer.getPaper().getCount());
        return rtn;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2' %}
    public boolean canCraftInDimensions(
            int pWidth,
            int pHeight
    ) {

        return true;
    }

    @MCVersionDependentBehaviour
    @Override
    public ItemStack getResultItem() {

        return ItemStack.EMPTY;
    }

    @MCVersionDependentBehaviour
    @Override
    public ResourceLocation getId() {

        return id;
    }

{% when '1.19.4', '1.20', '1.20.1' %}
    public boolean canCraftInDimensions(
            int pWidth,
            int pHeight
    ) {

        return true;
    }

    @MCVersionDependentBehaviour
    @Override
    public ItemStack getResultItem(RegistryAccess p_267052_) {

        return ItemStack.EMPTY;
    }

    @MCVersionDependentBehaviour
    @Override
    public ResourceLocation getId() {

        return id;
    }

{% when '1.20.2', '1.20.3', '1.20.4' %}
    public boolean canCraftInDimensions(
            int pWidth,
            int pHeight
    ) {

        return true;
    }

    @MCVersionDependentBehaviour
    @Override
    public ItemStack getResultItem(RegistryAccess p_267052_) {

        return ItemStack.EMPTY;
    }

{% when '1.21', '1.21.1' %}
    public boolean canCraftInDimensions(
            int pWidth,
            int pHeight
    ) {

        return true;
    }

    @MCVersionDependentBehaviour
    @Override
    public ItemStack getResultItem(HolderLookup.Provider pRegistries) {

        return ItemStack.EMPTY;
    }

{% when '26.1.2' %}
    public boolean showNotification() {

        return false;
    }

{% endcase %}
    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public RecipeSerializer<?> getSerializer() {
{% when '26.1.2' %}
    public String group() {

        return "";
    }

    @Override
    public RecipeSerializer<? extends Recipe<PrintingPressBlockEntity>> getSerializer() {
{% endcase %}

        return SFMRecipeSerializers.PRINTING_PRESS.get();
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public RecipeType<?> getType() {
{% when '26.1.2' %}
    public RecipeType<? extends Recipe<PrintingPressBlockEntity>> getType() {
{% endcase %}

        return SFMRecipeTypes.PRINTING_PRESS.get();
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return null;
    }

{% endcase %}
    @MCVersionDependentBehaviour
    @Override
    public boolean equals(Object obj) {

        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PrintingPressRecipe) obj;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        return Objects.equals(this.id, that.id) &&
               Objects.equals(this.form, that.form) &&
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return Objects.equals(this.form, that.form) &&
{% endcase %}
               Objects.equals(this.ink, that.ink) &&
               Objects.equals(this.paper, that.paper);
    }

    @MCVersionDependentBehaviour
    @Override
    public int hashCode() {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        return Objects.hash(id, form, ink, paper);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return Objects.hash(form, ink, paper);
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public String toString() {
{% when '26.1.2' %}
    public @NonNull String toString() {
{% endcase %}

        return "PrintingPressRecipe[" +
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
               "id=" + id + ", " +
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
               "form=" + form + ", " +
               "ink=" + ink + ", " +
               "paper=" + paper + ']';
    }

    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public static class Serializer implements RecipeSerializer<PrintingPressRecipe> {
        @Override
        public PrintingPressRecipe fromJson(
                ResourceLocation pRecipeId,
                JsonObject pSerializedRecipe
        ) {

            Ingredient form = Ingredient.fromJson(pSerializedRecipe.get("form"));
            Ingredient ink = Ingredient.fromJson(pSerializedRecipe.get("ink"));
            Ingredient paper = Ingredient.fromJson(pSerializedRecipe.get("paper"));
            return new PrintingPressRecipe(pRecipeId, form, ink, paper);
        }

        @Override
        public @Nullable PrintingPressRecipe fromNetwork(
                ResourceLocation pRecipeId,
                FriendlyByteBuf pBuffer
        ) {
            Ingredient form = Ingredient.fromNetwork(pBuffer);
            Ingredient ink = Ingredient.fromNetwork(pBuffer);
            Ingredient paper = Ingredient.fromNetwork(pBuffer);
            return new PrintingPressRecipe(pRecipeId, form, ink, paper);
        }

        @Override
        public void toNetwork(
                FriendlyByteBuf pBuffer,
                PrintingPressRecipe pRecipe
        ) {
            pRecipe.form.toNetwork(pBuffer);
            pRecipe.ink.toNetwork(pBuffer);
            pRecipe.paper.toNetwork(pBuffer);
        }

    }
{% when '1.20.2', '1.20.3', '1.20.4' %}
    public static class Serializer implements RecipeSerializer<PrintingPressRecipe> {
        private final Codec<PrintingPressRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC.fieldOf("form").forGetter(PrintingPressRecipe::form),
                Ingredient.CODEC.fieldOf("ink").forGetter(PrintingPressRecipe::ink),
                Ingredient.CODEC.fieldOf("paper").forGetter(PrintingPressRecipe::paper)
        ).apply(instance, PrintingPressRecipe::new));

        @Override
        public Codec<PrintingPressRecipe> codec() {
            return CODEC;
        }

        @Override
        public PrintingPressRecipe fromNetwork(FriendlyByteBuf friendlyByteBuf) {
            Ingredient form = Ingredient.fromNetwork(friendlyByteBuf);
            Ingredient ink = Ingredient.fromNetwork(friendlyByteBuf);
            Ingredient paper = Ingredient.fromNetwork(friendlyByteBuf);
            return new PrintingPressRecipe(form, ink, paper);
        }

        @Override
        public void toNetwork(
                FriendlyByteBuf pBuffer,
                PrintingPressRecipe pRecipe
        ) {
            pRecipe.form.toNetwork(pBuffer);
            pRecipe.ink.toNetwork(pBuffer);
            pRecipe.paper.toNetwork(pBuffer);
        }

    }
{% when '1.21', '1.21.1' %}
    public static class Serializer implements RecipeSerializer<PrintingPressRecipe> {
        private final MapCodec<PrintingPressRecipe> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
                Ingredient.CODEC.fieldOf("form").forGetter(PrintingPressRecipe::form),
                Ingredient.CODEC.fieldOf("ink").forGetter(PrintingPressRecipe::ink),
                Ingredient.CODEC.fieldOf("paper").forGetter(PrintingPressRecipe::paper)
        ).apply(builder, PrintingPressRecipe::new));

        private final StreamCodec<RegistryFriendlyByteBuf, PrintingPressRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork
        );

        @Override
        public MapCodec<PrintingPressRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PrintingPressRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        public static PrintingPressRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            Ingredient form = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            Ingredient ink = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            Ingredient paper = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            return new PrintingPressRecipe(form, ink, paper);
        }

        public static void toNetwork(
                RegistryFriendlyByteBuf buf,
                PrintingPressRecipe pRecipe
        ) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, pRecipe.form);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, pRecipe.ink);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, pRecipe.paper);
        }

    }
{% when '26.1.2' %}
    public static final MapCodec<PrintingPressRecipe> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            Ingredient.CODEC.fieldOf("form").forGetter(PrintingPressRecipe::form),
            Ingredient.CODEC.fieldOf("ink").forGetter(PrintingPressRecipe::ink),
            Ingredient.CODEC.fieldOf("paper").forGetter(PrintingPressRecipe::paper)
    ).apply(builder, PrintingPressRecipe::new));
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    @MCVersionDependentBehaviour
    public static final StreamCodec<RegistryFriendlyByteBuf, PrintingPressRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, PrintingPressRecipe::form,
                    Ingredient.CONTENTS_STREAM_CODEC, PrintingPressRecipe::ink,
                    Ingredient.CONTENTS_STREAM_CODEC, PrintingPressRecipe::paper,
                    PrintingPressRecipe::new
            );
{% endcase %}
}
