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
        List<PrintingPressRecipe> printingPressRecipes = new ArrayList<>();
        List<FallingAnvilRecipe> fallingAnvilRecipes = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        assert level != null;
        RecipeManager recipeManager = level.getRecipeManager();
        recipeManager.getAllRecipesFor(SFMRecipeTypes.PRINTING_PRESS.get()).forEach(r -> {
            printingPressRecipes.add(r);
            fallingAnvilRecipes.add(new FallingAnvilFormRecipe(r));
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
                        .map(b -> new Rect2i(
                                SFMWidgetUtils.getX(b),
                                SFMWidgetUtils.getY(b),
                                b.getWidth(),
                                b.getHeight()
                        ))
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
