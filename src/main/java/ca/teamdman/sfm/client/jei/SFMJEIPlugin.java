package ca.teamdman.sfm.client.jei;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.gui.screen.ManagerScreen;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class SFMJEIPlugin implements IModPlugin {
    public @Nullable IJeiRuntime jeiRuntime = null;
    public static @Nullable SFMJEIPlugin INSTANCE = null;

    public SFMJEIPlugin() {
        if (INSTANCE != null) {
            throw new IllegalStateException("Tried to create multiple instances of SFMJEIPlugin");
        }
        INSTANCE = this;
    }

    public static @Nullable IJeiRuntime getJeiRuntime() {
        return INSTANCE != null ? INSTANCE.jeiRuntime : null;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(SFM.MOD_ID, "sfm");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new PrintingPressJEICategory(registration.getJeiHelpers()),
                new FallingAnvilJEICategory(registration.getJeiHelpers())
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(SFMBlocks.PRINTING_PRESS_BLOCK.get()),
                PrintingPressJEICategory.RECIPE_TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(Blocks.ANVIL),
                FallingAnvilJEICategory.RECIPE_TYPE
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<PrintingPressRecipe> printingPressRecipes = new ArrayList<>();
        List<FallingAnvilRecipe> fallingAnvilRecipes = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        assert level != null;
        RecipeManager recipeManager = level.getRecipeManager();
        recipeManager.getAllRecipesFor(SFMRecipeTypes.PRINTING_PRESS.get()).forEach(r -> {
            printingPressRecipes.add(r.value());
            fallingAnvilRecipes.add(new FallingAnvilFormRecipe(r.value()));
        });
        fallingAnvilRecipes.add(new FallingAnvilDisenchantRecipe());
        fallingAnvilRecipes.add(new FallingAnvilExperienceShardRecipe());
        registration.addRecipes(PrintingPressJEICategory.RECIPE_TYPE, printingPressRecipes);
        registration.addRecipes(FallingAnvilJEICategory.RECIPE_TYPE, fallingAnvilRecipes);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(ManagerScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(ManagerScreen screen) {
                var buttons = screen.getButtonsForJEIExclusionZones();
                return buttons
                        .stream()
                        .filter(b -> b.visible)
                        .map(b -> new Rect2i(b.getX(), b.getY(), b.getWidth(), b.getHeight()))
                        .toList();
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        this.jeiRuntime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
    }
}
