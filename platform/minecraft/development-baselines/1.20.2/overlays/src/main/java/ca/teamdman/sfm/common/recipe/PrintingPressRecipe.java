package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeTypes;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
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
    public ItemStack assemble(PrintingPressBlockEntity pContainer, RegistryAccess p_267165_) {
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
    public ItemStack getResultItem(RegistryAccess p_267052_) {
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

}
