package ca.teamdman.sfm.client.jei;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.client.screen.SFMWidgetUtils;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeTypes;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class SFMJEIPlugin implements IModPlugin {
    public static @Nullable SFMJEIPlugin INSTANCE = null;

    public @Nullable IJeiRuntime jeiRuntime = null;

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

        return SFMResourceLocation.fromSFMPath(SFM.MOD_ID);
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
                new ItemStack(SFMBlocks.PRINTING_PRESS.get()),
                PrintingPressJEICategory.RECIPE_TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(Blocks.ANVIL),
                FallingAnvilJEICategory.RECIPE_TYPE
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {

        // Acquire recipe manager
        var level = Minecraft.getInstance().level;
        assert level != null;
        RecipeManager recipeManager = level.getRecipeManager();

        // Get the list of printing press recipes from the recipe manager
        List<RecipeHolder<PrintingPressRecipe>> printingPressRecipes = recipeManager.getAllRecipesFor(SFMRecipeTypes.PRINTING_PRESS.get());

        // Create results collections
        List<PrintingPressRecipe> jeiPrintingPressRecipes = new ArrayList<>();
        List<FallingAnvilRecipe> jeiFallingAnvilRecipes = new ArrayList<>();

        // Identify recipes for printing press, and the falling anvil recipes that create the forms for those recipes.
        for (RecipeHolder<PrintingPressRecipe> r : printingPressRecipes) {

            // Get the recipe
            PrintingPressRecipe printingPressRecipe = r.value();

            // Add the printing press recipe
            jeiPrintingPressRecipes.add(printingPressRecipe);

            // Add the falling anvil recipe
            jeiFallingAnvilRecipes.add(new FallingAnvilFormRecipe(printingPressRecipe));
        }

        // Add the falling anvil recipe for disenchanting
        jeiFallingAnvilRecipes.add(new FallingAnvilDisenchantRecipe());

        // Add the falling anvil recipe for turning enchanted books into experience shards
        jeiFallingAnvilRecipes.add(new FallingAnvilExperienceShardRecipe());

        // Submit the recipes to JEI
        registration.addRecipes(PrintingPressJEICategory.RECIPE_TYPE, jeiPrintingPressRecipes);
        registration.addRecipes(FallingAnvilJEICategory.RECIPE_TYPE, jeiFallingAnvilRecipes);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {

        registration.addGuiContainerHandler(
                ManagerScreen.class, new IGuiContainerHandler<>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(ManagerScreen screen) {

                        var buttons = screen.getButtonsForJEIExclusionZones();
                        return buttons
                                .stream()
                                .filter(b -> b.visible)
                                .map(b -> new Rect2i(
                                        SFMWidgetUtils.getX(b),
                                        SFMWidgetUtils.getY(b),
                                        b.getWidth(),
                                        b.getHeight()
                                ))
                                .toList();
                    }
                }
        );
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
