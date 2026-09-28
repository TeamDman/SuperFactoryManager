package ca.teamdman.sfm.client.export;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.jei.SFMJEIPlugin;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.library.ingredients.IIngredientSupplier;
import mezz.jei.library.util.IngredientSupplierHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ClientExportHelper {

    private static final Object registryReaderLock = new Object();

    public static Collection<ItemStack> gatherItems() {
        assert Minecraft.getInstance().player != null;
        assert Minecraft.getInstance().level != null;
        CreativeModeTabs.tryRebuildTabContents(
                Minecraft.getInstance().player.connection.enabledFeatures(),
                true,
                Minecraft.getInstance().level.registryAccess()
        );
        return CreativeModeTabs.searchTab().getDisplayItems();
    }

    // https://github.com/TeamDman/tell-me-my-items/blob/6fb767f0145abebff503b87a10a1810ca24580b9/mod/src/main/java/ca/teamdman/tellmemyitems/TellMeMyItems.java#L36
    public static void dumpItems(@Nullable Player player) throws IOException {
        // manually build JSON array
        JsonArray jsonArray = new JsonArray();

        var items = gatherItems();
        for (ItemStack stack : items) {
            JsonObject jsonObject = new JsonObject();

            // Add the id field
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            assert id != null;
            jsonObject.addProperty("id", id.toString());

            // Add the data field if it exists
            if (stack.getShareTag() != null) {
                jsonObject.addProperty("data", stack.getShareTag().toString());
            }

            // Add the tags
            JsonArray tags = new JsonArray();
            SFMResourceTypes.ITEM.get().getTagsForStack(stack).map(ResourceLocation::toString).forEach(tags::add);
            jsonObject.add("tags", tags);

            // Add the tooltip field (requires player)
            String tooltip = stack.getTooltipLines(player, TooltipFlag.ADVANCED)
                    .stream()
                    .map(Component::getString)
                    .reduce((line1, line2) -> line1 + "\n" + line2)
                    .orElse("");
            jsonObject.addProperty("tooltip", tooltip);

            jsonArray.add(jsonObject);
        }

        // serialize to JSON with pretty printing
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String content = gson.toJson(jsonArray);

        // ensure folder exists
        var gameDir = FMLPaths.GAMEDIR.get();
        var folder = Paths.get(gameDir.toString(), SFM.MOD_ID);
        Files.createDirectories(folder);

        // write to file
        File itemFile = new File(folder.toFile(), "items.json");
        try (FileOutputStream str = new FileOutputStream(itemFile)) {
            str.write(content.getBytes(StandardCharsets.UTF_8));
        }
        SFM.LOGGER.info("Exported item data to {}", itemFile);
        assert Minecraft.getInstance().player != null;
        Minecraft.getInstance().player.sendSystemMessage(Component.literal(String.format(
                "Exported %d items to \"%s\"",
                items.size(),
                itemFile.getAbsolutePath()
        )));
    }

    public static void dumpJei(
            Player player,
            boolean includeHidden
    ) throws IOException {
        IJeiRuntime jeiRuntime = SFMJEIPlugin.getJeiRuntime();
        if (jeiRuntime == null) {
            String msg = "No JEI runtime detected, no recipes have been exported";
            SFM.LOGGER.error(msg);
            player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
            return;
        }
        IJeiHelpers jeiHelpers = jeiRuntime.getJeiHelpers();
        IIngredientManager ingredientManager = jeiRuntime.getIngredientManager();
        IRecipeManager recipeManager = jeiRuntime.getRecipeManager();
        Stream<IRecipeCategory<?>> recipeCategoryStream = recipeManager
                .createRecipeCategoryLookup()
                .includeHidden()
                .get();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Collection<ResourceType<?, ?, ?>> resourceTypes = new ArrayList<>();
        SFMResourceTypes.registry().stream().forEach(resourceTypes::add);

        // Ensure the folder exists
        var gameDir = FMLPaths.GAMEDIR.get();
        var folder = Paths.get(gameDir.toString(), SFM.MOD_ID, "jei");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            SFM.LOGGER.error("Failed to create directories: {}", folder.toString(), e);
            player.sendSystemMessage(Component.literal("Failed to create directories for saving recipes.")
                                             .withStyle(ChatFormatting.RED));
            return;
        }

        // Process each recipe category in parallel
        recipeCategoryStream.parallel().forEach(recipeCategory -> {
            ConcurrentLinkedDeque<JsonObject> recipeResults = new ConcurrentLinkedDeque<>();
            AtomicInteger counter = new AtomicInteger();
            AtomicInteger fileIndex = new AtomicInteger();

            extractCategory(gson, jeiHelpers, recipeCategory, recipeManager, ingredientManager, recipeResults,
                            resourceTypes, includeHidden, player, counter, fileIndex, folder
            );

            // Write any remaining recipes in the final chunk
            if (!recipeResults.isEmpty()) {
                writeChunkToFile(gson, recipeResults, recipeCategory, fileIndex.getAndIncrement(), folder, player);
            }

            // Notify the player that the category is fully processed
            player.sendSystemMessage(Component.literal(String.format(
                    "Completed exporting all JEI entries for category \"%s\"",
                    recipeCategory.getTitle().getString()
            )));
        });
    }

    private static <T> void extractCategory(
            Gson gson,
            IJeiHelpers jeiHelpers,
            IRecipeCategory<T> recipeCategory,
            IRecipeManager recipeManager,
            IIngredientManager ingredientManager,
            ConcurrentLinkedDeque<JsonObject> recipeResults,
            Collection<ResourceType<?, ?, ?>> resourceTypes,
            boolean includeHidden,
            Player player,
            AtomicInteger counter,
            AtomicInteger fileIndex,
            java.nio.file.Path folder
    ) {
        String categoryString = recipeCategory.toString();
        String categoryTitle = recipeCategory.getTitle().getString();

        RecipeType<T> recipeType = recipeCategory.getRecipeType();
        IRecipeLookup<T> recipeLookup = recipeManager.createRecipeLookup(recipeType);
        if (includeHidden) {
            recipeLookup = recipeLookup.includeHidden();
        }
        Stream<T> recipes = recipeLookup.get();

        recipes.parallel().forEach(recipe -> {
            IIngredientSupplier ingredientSupplier = IngredientSupplierHelper.getIngredientSupplier(
                    recipe,
                    recipeCategory,
                    ingredientManager
            );
            if (ingredientSupplier == null) {
                player.sendSystemMessage(
                        Component.literal("Could not get ingredient supplier for recipe from category ")
                                .withStyle(ChatFormatting.RED)
                                .append(recipeCategory.getTitle())
                                .append(Component.literal(" with recipe object "))
                                .append(Component.literal(recipe.toString()))
                );
                return;
            }

            ConcurrentLinkedDeque<JsonObject> ingredientResults = new ConcurrentLinkedDeque<>();

            // Build ingredient info
            JsonArray ingredientArray = new JsonArray();

            for (RecipeIngredientRole recipeIngredientRole : RecipeIngredientRole.values()) {
                ingredientSupplier.getIngredients(recipeIngredientRole).stream().parallel()
                        .forEachOrdered(typedIngredient -> {
                            JsonObject ingredientObject = new JsonObject();
                            ingredientObject.addProperty("role", recipeIngredientRole.toString());
                            ingredientObject.addProperty(
                                    "ingredientType",
                                    typedIngredient.getType().getIngredientClass().getName()
                            );
                            Object ingredient = typedIngredient.getIngredient();
                            //noinspection rawtypes
                            for (ResourceType resourceType : resourceTypes) {
                                if (resourceType.matchesStackType(ingredient)) {
                                    //noinspection unchecked
                                    addIngredientInfo(resourceType, ingredient, ingredientObject);
                                }
                            }
                            ingredientObject.addProperty("ingredient", ingredient.toString());
                            ingredientResults.add(ingredientObject);
                        });
            }

            ingredientResults.forEach(ingredientArray::add);

            // Add results
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("category", categoryString);
            jsonObject.addProperty("categoryTitle", categoryTitle);
            jsonObject.addProperty("recipeTypeId", recipeType.getUid().toString());
            jsonObject.addProperty("recipeClass", recipeType.getRecipeClass().toString());
            jsonObject.addProperty("recipeObject", recipe.toString());
            jsonObject.add("ingredients", ingredientArray);
            recipeResults.add(jsonObject);

            int count = counter.incrementAndGet();
            if (count % 1000 == 0) {
                // Write chunk to disk
                writeChunkToFile(gson, recipeResults, recipeCategory, fileIndex.getAndIncrement(), folder, player);
                recipeResults.clear(); // Clear the list to free up memory
            }

            if (count > 0 && count % 1000 == 0) {
                // notify the player
                player.sendSystemMessage(Component.literal(String.format(
                        "Processed %d recipes so far for category \"%s\"",
                        count,
                        categoryTitle
                )));
            }
        });
    }

    private static void writeChunkToFile(
            Gson gson,
            ConcurrentLinkedDeque<JsonObject> recipeResults,
            IRecipeCategory<?> recipeCategory,
            int chunkIndex,
            java.nio.file.Path folder,
            Player player
    ) {
        String fileName = recipeCategory.getTitle().getString().replaceAll("[<>:\"/\\\\|?*]", "-")
                          + "-"
                          + chunkIndex
                          + ".json";
        File file = new File(folder.toFile(), fileName);

        JsonArray jsonArray = new JsonArray();
        recipeResults.forEach(jsonArray::add);

        String content = gson.toJson(jsonArray);
        try (FileOutputStream str = new FileOutputStream(file)) {
            str.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            SFM.LOGGER.error(
                    "Failed to write JEI category data for category: {}",
                    recipeCategory.getTitle().getString(),
                    e
            );
            player.sendSystemMessage(Component.literal("Failed to save recipe category chunk: ")
                                             .append(recipeCategory.getTitle()).withStyle(ChatFormatting.RED));
        }
    }


    private static <STACK, ITEM, CAP> void addIngredientInfo(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack,
            JsonObject ingredientObject
    ) {
        long amount = resourceType.getAmount(stack);
        ingredientObject.addProperty("ingredientAmount", amount);

        ResourceLocation stackRegistryKey;
        synchronized (registryReaderLock) {
            stackRegistryKey = resourceType.getRegistryKeyForStack(stack);
        }
        ingredientObject.addProperty("ingredientId", stackRegistryKey.toString());

        JsonArray tags = new JsonArray();
        resourceType.getTagsForStack(stack).map(ResourceLocation::toString).forEach(tags::add);
        ingredientObject.add("tags", tags);
    }
}
