package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.registry.SFMRecipeSerializers;
import ca.teamdman.sfm.common.registry.SFMRecipeTypes;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Printing press copies a form using ink and paper.
 */
public class PrintingPressRecipe implements Recipe<PrintingPressBlockEntity> {
    public final ResourceLocation ID;
    public final Ingredient FORM;
    public final Ingredient INK;
    public final Ingredient PAPER;

    /**
     *
     */
    public PrintingPressRecipe(
            ResourceLocation id,
            Ingredient form,
            Ingredient ink,
            Ingredient paper
    ) {
        this.ID = id;
        this.FORM = form;
        this.INK = ink;
        this.PAPER = paper;
    }

    @Override
    public boolean matches(PrintingPressBlockEntity pContainer, Level pLevel) {
        return PAPER.test(pContainer.getPaper())
               && INK.test(pContainer.getInk())
               && FORM.test(FormItem.getBorrowedReferenceFromForm(pContainer.getForm()));
    }

    @Override
    public ItemStack assemble(PrintingPressBlockEntity pContainer, RegistryAccess p_267165_) {
        ItemStack rtn = FormItem.getCopiedReferenceFromForm(pContainer.getForm());
        rtn.setCount(pContainer.getPaper().getCount());
        return rtn;
    }

    @Override
    public boolean canCraftInDimensions(int pWidth, int pHeight) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess p_267052_) {
        return ItemStack.EMPTY;

    }


    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SFMRecipeSerializers.PRINTING_PRESS.get();
    }

    @Override
    public RecipeType<?> getType() {
        return SFMRecipeTypes.PRINTING_PRESS.get();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PrintingPressRecipe) obj;
        return Objects.equals(this.ID, that.ID) &&
               Objects.equals(this.FORM, that.FORM) &&
               Objects.equals(this.INK, that.INK) &&
               Objects.equals(this.PAPER, that.PAPER);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ID, FORM, INK, PAPER);
    }

    @Override
    public String toString() {
        return "PrintingPressRecipe[" +
               "id=" + ID + ", " +
               "form=" + FORM + ", " +
               "ink=" + INK + ", " +
               "paper=" + PAPER + ']';
    }

    public static class Serializer implements RecipeSerializer<PrintingPressRecipe> {
        @Override
        public PrintingPressRecipe fromJson(ResourceLocation pRecipeId, JsonObject pSerializedRecipe) {
            Ingredient form = Ingredient.fromJson(pSerializedRecipe.get("form"));
            Ingredient ink = Ingredient.fromJson(pSerializedRecipe.get("ink"));
            Ingredient paper = Ingredient.fromJson(pSerializedRecipe.get("paper"));
            return new PrintingPressRecipe(pRecipeId, form, ink, paper);
        }

        @Override
        public @Nullable PrintingPressRecipe fromNetwork(ResourceLocation pRecipeId, FriendlyByteBuf pBuffer) {
            Ingredient form = Ingredient.fromNetwork(pBuffer);
            Ingredient ink = Ingredient.fromNetwork(pBuffer);
            Ingredient paper = Ingredient.fromNetwork(pBuffer);
            return new PrintingPressRecipe(pRecipeId, form, ink, paper);
        }

        @Override
        public void toNetwork(FriendlyByteBuf pBuffer, PrintingPressRecipe pRecipe) {
            pRecipe.FORM.toNetwork(pBuffer);
            pRecipe.INK.toNetwork(pBuffer);
            pRecipe.PAPER.toNetwork(pBuffer);
        }
    }

}
