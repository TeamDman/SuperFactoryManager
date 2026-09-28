package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeTypes;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Printing press copies a form using ink and paper.
 */
public record PrintingPressRecipe(
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
    public ItemStack assemble(
            PrintingPressBlockEntity pContainer,
            HolderLookup.Provider provider
    ) {
        ItemStack rtn = FormItem.getCopiedReferenceFromForm(pContainer.getForm());
        rtn.setCount(pContainer.getPaper().getCount());
        return rtn;
    }

    @Override
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

    @Override
    public RecipeSerializer<?> getSerializer() {

        return SFMRecipeSerializers.PRINTING_PRESS.get();
    }

    @Override
    public RecipeType<?> getType() {

        return SFMRecipeTypes.PRINTING_PRESS.get();
    }

    @MCVersionDependentBehaviour
    @Override
    public boolean equals(Object obj) {

        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PrintingPressRecipe) obj;
        return Objects.equals(this.form, that.form) &&
               Objects.equals(this.ink, that.ink) &&
               Objects.equals(this.paper, that.paper);
    }

    @MCVersionDependentBehaviour
    @Override
    public int hashCode() {

        return Objects.hash(form, ink, paper);
    }

    @Override
    public String toString() {

        return "PrintingPressRecipe[" +
               "form=" + form + ", " +
               "ink=" + ink + ", " +
               "paper=" + paper + ']';
    }

    @MCVersionDependentBehaviour
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

}
