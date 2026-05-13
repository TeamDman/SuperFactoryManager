package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeTypes;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

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
            PrintingPressBlockEntity pContainer
    ) {
        ItemStack rtn = FormItem.getCopiedReferenceFromForm(pContainer.getForm());
        rtn.setCount(pContainer.getPaper().getCount());
        return rtn;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<? extends Recipe<PrintingPressBlockEntity>> getSerializer() {

        return SFMRecipeSerializers.PRINTING_PRESS.get();
    }

    @Override
    public RecipeType<? extends Recipe<PrintingPressBlockEntity>> getType() {

        return SFMRecipeTypes.PRINTING_PRESS.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return null;
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
    public @NonNull String toString() {

        return "PrintingPressRecipe[" +
               "form=" + form + ", " +
               "ink=" + ink + ", " +
               "paper=" + paper + ']';
    }

    @MCVersionDependentBehaviour
    public static final MapCodec<PrintingPressRecipe> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            Ingredient.CODEC.fieldOf("form").forGetter(PrintingPressRecipe::form),
            Ingredient.CODEC.fieldOf("ink").forGetter(PrintingPressRecipe::ink),
            Ingredient.CODEC.fieldOf("paper").forGetter(PrintingPressRecipe::paper)
    ).apply(builder, PrintingPressRecipe::new));

    @MCVersionDependentBehaviour
    public static final StreamCodec<RegistryFriendlyByteBuf, PrintingPressRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, PrintingPressRecipe::form,
                    Ingredient.CONTENTS_STREAM_CODEC, PrintingPressRecipe::ink,
                    Ingredient.CONTENTS_STREAM_CODEC, PrintingPressRecipe::paper,
                    PrintingPressRecipe::new
            );
}
