package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.common.recipe.PrintingPressFinishedRecipe;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.registry.SFMRecipeSerializers;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.Tags;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.function.Consumer;

public class SFMRecipes extends RecipeProvider {
    public SFMRecipes(GatherDataEvent event) {
        super(event.getGenerator());
    }

    @Override
    protected void buildCraftingRecipes(Consumer<FinishedRecipe> consumer) {
        ShapedRecipeBuilder
                .shaped(SFMBlocks.CABLE_BLOCK.get(), 16)
                .define('D', Tags.Items.DYES_BLACK)
                .define('G', Items.LIGHT_WEIGHTED_PRESSURE_PLATE)
                .define('C', Tags.Items.CHESTS)
                .define('B', Items.IRON_BARS)
                .pattern("DGD")
                .pattern("BCB")
                .pattern("DGD")
                .unlockedBy("has_iron_ingot", RecipeProvider.has(Items.IRON_INGOT))
                .unlockedBy("has_chest", RecipeProvider.has(Tags.Items.CHESTS))
                .save(consumer);

        ShapelessRecipeBuilder
                .shapeless(SFMBlocks.CABLE_BLOCK_BLOCK.get())
                .requires(SFMBlocks.CABLE_BLOCK.get(), 1)
                .unlockedBy("has_iron_ingot", RecipeProvider.has(Items.IRON_INGOT))
                .unlockedBy("has_chest", RecipeProvider.has(Tags.Items.CHESTS))
                .save(consumer);

        ShapedRecipeBuilder
                .shaped(SFMBlocks.MANAGER_BLOCK.get())
                .define('A', Tags.Items.CHESTS)
                .define('B', SFMBlocks.CABLE_BLOCK.get())
                .define('C', Items.REPEATER)
                .unlockedBy("has_iron_ingot", RecipeProvider.has(Items.IRON_INGOT))
                .unlockedBy("has_chest", RecipeProvider.has(Tags.Items.CHESTS))
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ABA")
                .save(consumer);

        ShapedRecipeBuilder
                .shaped(SFMItems.LABEL_GUN_ITEM.get())
                .define('S', Tags.Items.RODS_WOODEN)
                .define('B', Tags.Items.DYES_BLACK)
                .define('L', Tags.Items.DYES_BLUE)
                .define('C', ItemTags.SIGNS)
                .unlockedBy("has_ink", RecipeProvider.has(Tags.Items.DYES_BLACK))
                .pattern(" LC")
                .pattern(" SB")
                .pattern("S  ")
                .save(consumer);


        ShapedRecipeBuilder
                .shaped(SFMItems.NETWORK_TOOL_ITEM.get())
                .define('S', Items.IRON_INGOT)
                .define('L', Items.REDSTONE_LAMP)
                .define('P', Items.HEAVY_WEIGHTED_PRESSURE_PLATE)
                .define('C', ItemTags.SIGNS)
                .unlockedBy("has_redstone_lamp", RecipeProvider.has(Items.REDSTONE_LAMP))
                .pattern(" LC")
                .pattern(" SP")
                .pattern("S  ")
                .save(consumer);


        ShapedRecipeBuilder
                .shaped(SFMItems.DISK_ITEM.get())
                .define('R', Blocks.REDSTONE_BLOCK)
                .define('e', Items.REDSTONE)
                .define('d', Items.REPEATER)
                .define('a', Tags.Items.DYES_RED)
                .define('b', Tags.Items.DYES_GREEN)
                .define('c', Tags.Items.DYES_BLUE)
                .define('p', Items.PAPER)
                .unlockedBy("has_redstone", RecipeProvider.has(Items.REDSTONE))
                .pattern("pbp")
                .pattern("aRc")
                .pattern("ede")
                .save(consumer);

        ShapedRecipeBuilder
                .shaped(SFMItems.WATER_TANK_ITEM.get())
                .define('b', Items.WATER_BUCKET)
                .define('g', Items.IRON_BARS)
                .define('p', Items.LIGHT_WEIGHTED_PRESSURE_PLATE)
                .unlockedBy("has_water", RecipeProvider.has(Items.WATER_BUCKET))
                .pattern("gbg")
                .pattern("gpg")
                .pattern("gbg")
                .save(consumer);

        ShapelessRecipeBuilder
                .shapeless(SFMItems.EXPERIENCE_GOOP_ITEM.get())
                .requires(SFMItems.EXPERIENCE_SHARD_ITEM.get(), 9)
                .unlockedBy("has_experience_shard", RecipeProvider.has(SFMItems.EXPERIENCE_SHARD_ITEM.get()))
                .save(consumer);


        ShapedRecipeBuilder
                .shaped(SFMItems.PRINTING_PRESS_ITEM.get())
                .define('a', Items.ANVIL)
                .define('i', Tags.Items.DYES_BLACK)
                .define('p', Items.LIGHT_WEIGHTED_PRESSURE_PLATE)
                .define('s', Items.STONE)
                .define('x', Items.PISTON)
                .define('g', Items.IRON_BARS)
                .unlockedBy("has_iron", RecipeProvider.has(Items.IRON_INGOT))
                .pattern("pip")
                .pattern("sas")
                .pattern("gxg")
                .save(consumer);

        addPrintingPressRecipe(
                consumer,
                new ResourceLocation("sfm", "written_book_copy"),
                Ingredient.of(Items.WRITTEN_BOOK),
                Ingredient.of(Tags.Items.DYES_BLACK),
                Ingredient.of(Items.BOOK)
        );

        addPrintingPressRecipe(
                consumer,
                new ResourceLocation("sfm", "enchanted_book_copy"),
                Ingredient.of(Items.ENCHANTED_BOOK),
                Ingredient.of(SFMItems.EXPERIENCE_GOOP_ITEM.get()),
                Ingredient.of(Items.BOOK)
        );

        addPrintingPressRecipe(
                consumer,
                new ResourceLocation("sfm", "map_copy"),
                Ingredient.of(Items.FILLED_MAP),
                Ingredient.of(Tags.Items.DYES_BLACK),
                Ingredient.of(Items.MAP)
        );

        addPrintingPressRecipe(
                consumer,
                new ResourceLocation("sfm", "program_copy"),
                Ingredient.of(SFMItems.DISK_ITEM.get()),
                Ingredient.of(Tags.Items.DYES_BLACK),
                Ingredient.of(SFMItems.DISK_ITEM.get())
        );

        SpecialRecipeBuilder
                .special(SFMRecipeSerializers.DISK_RESET.get())
                .save(consumer, SFMRecipeSerializers.DISK_RESET.getId().getPath());
    }

    private void addPrintingPressRecipe(
            Consumer<FinishedRecipe> consumer,
            ResourceLocation id,
            Ingredient form,
            Ingredient ink,
            Ingredient paper
    ) {
        consumer.accept(new PrintingPressFinishedRecipe(id, form, ink, paper));
    }
}
