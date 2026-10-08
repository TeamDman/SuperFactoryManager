package ca.teamdman.sfm.client.jei;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.item_details_jei %}
import ca.teamdman.sfm.client.inspection.SFMItemInspectionDocument;
{% endif %}
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.item_details_jei %}
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.constants.VanillaTypes;
{% endif %}
import net.minecraft.client.Minecraft;
{% when "1.19.4", "1.20" %}
import net.minecraft.client.Minecraft;
{% when "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
{% when "26.1.2" %}
import mezz.jei.api.runtime.IJeiRuntime;
{% endcase %}
import net.minecraft.client.renderer.Rect2i;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21" %}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
{% when "1.21.1" %}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
{% endcase %}
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
{% case minecraft_version %}
{% when "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import org.jetbrains.annotations.Nullable;
{% when "26.1.2" %}
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
{% endcase %}
import java.util.ArrayList;
{% case minecraft_version %}
{% when "26.1.2" %}
import java.util.Collection;
{% endcase %}
import java.util.List;
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.item_details_jei %}
import java.util.Optional;
{% endif %}
{% endcase %}

@JeiPlugin
public class SFMJEIPlugin implements IModPlugin {
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.item_details_jei %}
    private static volatile IJeiRuntime runtime;

    /** Returns the ingredient currently under JEI's mouse overlay, if JEI is ready. */
    public static Optional<ItemStack> hoveredItemStack() {
        IJeiRuntime current = runtime;
        if (current == null) return Optional.empty();
        ItemStack stack = current.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        return stack == null || stack.isEmpty() ? Optional.empty() : Optional.of(stack.copy());
    }

{% endif %}
{% when "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21" %}
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

{% when "1.21.1", "26.1.2" %}
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

{% endcase %}
    @Override
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.item_details_jei %}
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        SFMJEIPlugin.runtime = runtime;
        SFMItemInspectionDocument.setHoveredIngredientSource(SFMJEIPlugin::hoveredItemStack);
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        SFMItemInspectionDocument.setHoveredIngredientSource(null);
    }

    @Override
{% endif %}
    public ResourceLocation getPluginUid() {
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21" %}
    public ResourceLocation getPluginUid() {
{% when "1.21.1" %}
    public ResourceLocation getPluginUid() {
{% when "26.1.2" %}
    public Identifier getPluginUid() {
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        registration.addRecipeCatalyst(
                new ItemStack(SFMBlocks.PRINTING_PRESS.get()),
                PrintingPressJEICategory.RECIPE_TYPE
{% when "26.1.2" %}
        registration.addCraftingStation(
                PrintingPressJEICategory.RECIPE_TYPE,
                SFMBlocks.PRINTING_PRESS.get()
{% endcase %}
        );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        registration.addRecipeCatalyst(
                new ItemStack(Blocks.ANVIL),
                FallingAnvilJEICategory.RECIPE_TYPE
{% when "26.1.2" %}
        registration.addCraftingStation(
                FallingAnvilJEICategory.RECIPE_TYPE,
                Blocks.ANVIL
{% endcase %}
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
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
{% when "1.20.2", "1.20.3", "1.20.4", "1.21" %}
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
{% when "1.21.1" %}

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
{% when "26.1.2" %}

        // Acquire recipe manager
        assert ServerLifecycleHooks.getCurrentServer() != null;
        RecipeManager recipeManager = ServerLifecycleHooks.getCurrentServer().getRecipeManager();

        // Get the list of printing press recipes from the recipe manager
        Collection<RecipeHolder<PrintingPressRecipe>> printingPressRecipes = recipeManager.recipeMap()
                .byType(SFMRecipeTypes.PRINTING_PRESS.get());

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
{% endcase %}
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
{% case minecraft_version %}
{% when "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21" %}

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        this.jeiRuntime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
    }
{% when "1.21.1", "26.1.2" %}

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        this.jeiRuntime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
    }
{% endcase %}
}
